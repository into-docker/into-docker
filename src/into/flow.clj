(ns into.flow
  (:require [into.flow
             [collect-sources :as collect-sources]
             [commit :as commit]
             [containers :as containers]
             [exec :as exec]
             [log :as log]
             [pull-image :as pull-image]
             [transfer-sources :as transfer-sources]]
            [clojure.java.io :as io])
  (:import [java.io InterruptedIOException]
           [sun.misc Signal SignalHandler]))

;; ## Helper

(defn- validate
  ([data path error-message]
   (validate data path some? error-message))
  ([data path pred ^String error-message]
   (if (pred (get-in data path))
     data
     (assoc data :error (IllegalStateException. error-message)))))

(defn- register-shutdown-handler!
  [data]
  (let [^Thread t (Thread/currentThread)] (Signal/handle
    (Signal. "INT")
    (reify SignalHandler
      (handle [this signal]
        (.interrupt t)))))
  data)

(defn- handle-interrupt
  [data e]
  (if (Thread/interrupted)
    (assoc data ::interrupt? true)
    (assoc data :error e)))

(defmacro with-flow->
  [form nxt & rst]
  (if (empty? rst)
    `(let [value# ~form]
       (if-not (or (:error value#) (::interrupt? value#))
         (try
           (-> value# ~nxt)
           (catch InterruptedException e#
             (handle-interrupt value# e#))
           (catch InterruptedIOException e#
             (handle-interrupt value# e#))
           (catch Exception e#
             (assoc value# :error e#)))
         value#))
    `(with-flow->
       (with-flow-> ~form ~nxt)
       ~@rst)))

;; ## Pull

(defn- pull-builder-image!
  [data]
  (with-flow-> data
    (pull-image/pull-image :builder)
    (validate [:builder] "Builder image not found.")))

(defn- pull-runner-image!
  [data]
  (with-flow-> data
    (pull-image/pull-image :runner)
    (validate [:runner] "Runner image not found.")))

;; ## Runner Selection

(defn- select-runner-image
  [{:keys [builder] :as data}]
  (with-flow-> data
    (update-in
     [:spec :runner]
     #(or % (get-in builder [:labels :into.v1.runner])))
    (validate [:spec :runner] "No runner image given.")))

;; ## Start

(defn- start-builder!
  [data]
  (containers/run data :builder))

(defn- start-runner!
  [data]
  (containers/run data :runner))

;; ## Copy

(defn- copy-source-directory!
  [{:keys [builder] :as data}]
  (with-flow-> data
    (collect-sources/collect-sources)
    (transfer-sources/transfer-sources :builder)))

(defn- copy-artifacts!
  [{{:keys [assemble-script
            artifact-directory
            working-directory]} :paths
    :as data}]
  (with-flow-> data
    (containers/cp
     [:builder assemble-script]
     [:runner working-directory])
    (containers/cp
     [:builder artifact-directory]
     [:runner working-directory])))

;; ## Execute

(defn- execute-build!
  [{{:keys [build-script
            source-directory
            artifact-directory]} :paths
    :as data}]
  (->> {"INTO_SOURCE_DIR"   source-directory
        "INTO_ARTIFACT_DIR" artifact-directory}
       (exec/exec data :builder [build-script])))

(defn- execute-assemble!
  [{{:keys [working-directory artifact-directory]} :paths,
    :as data}]
  (->> {"INTO_ARTIFACT_DIR" artifact-directory}
       (exec/exec data :runner [(str working-directory "/assemble")])))

;; ## Commit

(defn- overwrite-runner-cmd
  [data]
  (if-let [runner-cmd (get-in data [:builder :labels :into.v1.runner.cmd])]
    (assoc-in data [:runner :cmd] ["sh" "-c" runner-cmd])
    data))

(defn- commit!
  [data]
  (with-flow-> data
    (overwrite-runner-cmd)
    ;; TODO: Uncomment once we have a good concept on how to cache intermediate
    ;;       build results. My initial idea would be a file `.cache` that
    ;;       describes declaratively which paths to cache.
    ;; (commit/commit-container :builder "-builder")
    (commit/commit-container :runner "")))

;; ## Cleanup

(defn- cleanup!
  [data]
  (try
    (-> data
        (log/info "Cleaning up resources ...")
        (containers/cleanup :runner)
        (containers/cleanup :builder))
    data
    (catch Exception e
      (assoc data :cleanup-error e))))

;; ## Flow

(defn- verify-spec
  [data]
  (-> data
      (validate [:spec :sources]
                #(-> % io/file .isDirectory)
                "Path does not point at a directory.")))

(defn- finalize!
  [data]
  (-> data
      (cleanup!)
      (log/report-errors)
      (with-flow->
        (log/success "Image [%s] has been build successfully." :target))))

(defn run
  [client spec]
  (finalize!
   (with-flow-> {:client client
                 :spec   spec
                 :paths {:source-directory   "/tmp/src"
                         :artifact-directory "/tmp/artifacts"
                         :working-directory  "/tmp"
                         :ignore-file        "/into/ignore"
                         :build-script       "/into/bin/build"
                         :assemble-script    "/into/bin/assemble"}}
     (log/emph "Building image [%s] from '.' ..." :target :sources)
     (verify-spec)
     (register-shutdown-handler!)

     (log/info "Pulling images ...")
     (pull-builder-image!)
     (select-runner-image)
     (pull-runner-image!)

     (log/info "Starting environment [%s -> %s] ..." :builder :runner)
     (start-builder!)
     (start-runner!)

     (log/emph "Building artifacts in [%s] ..." :builder)
     (copy-source-directory!)
     (execute-build!)

     (log/emph "Assembling application in [%s] ..." :runner)
     (copy-artifacts!)
     (execute-assemble!)

     (log/emph "Saving image [%s] ..." :target)
     (commit!))))
