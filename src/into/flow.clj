(ns into.flow
  (:require [into.docker :as docker]
            [into.flow
             [collect-sources :as collect-sources]
             [containers :as containers]
             [exec :as exec]
             [log :as log]
             [pull-image :as pull-image]
             [transfer-sources :as transfer-sources]])
  (:import [java.util UUID]))

;; ## Constants

(def ^:private BUILD_SCRIPT    "/into/build")
(def ^:private ASSEMBLE_SCRIPT "/into/assemble")
(def ^:private INTO_SOURCE_DIR "/tmp/src")
(def ^:private INTO_ARTIFACT_DIR "/tmp/target")
(def ^:private RUNNER_WORKDIR  "/tmp")

(def ^:private ENV
  [(str "INTO_SOURCE_DIR=" INTO_SOURCE_DIR)
   (str "INTO_ARTIFACT_DIR=" INTO_ARTIFACT_DIR)])

;; ## Helper

(defn- validate
  [data path error-message]
  (if (get-in data path)
    data
    (assoc data :error (IllegalStateException. error-message))))

;; ## Pull

(defn- pull-builder-image!
  [data]
  (-> data
      (pull-image/pull-image :builder)
      (validate [:builder] "Builder image not found.")))

(defn- pull-runner-image!
  [data]
  (-> data
      (pull-image/pull-image :runner)
      (validate [:runner] "Runner image not found.")))

;; ## Runner Selection

(defn- select-runner-image
  [{:keys [builder] :as data}]
  (-> data
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
  (-> data
      (exec/exec :builder "mkdir \"$INTO_SOURCE_DIR\" && mkdir \"$INTO_ARTIFACT_DIR\"")
      (collect-sources/collect-sources)
      (transfer-sources/transfer-sources :builder INTO_SOURCE_DIR)))

(defn- copy-artifacts!
  [data]
  (-> data
      (containers/cp [:builder ASSEMBLE_SCRIPT] [:runner RUNNER_WORKDIR])
      (containers/cp [:builder INTO_ARTIFACT_DIR] [:runner RUNNER_WORKDIR])))

;; ## Execute

(defn- execute-build!
  [{:keys [builder] :as data}]
  (exec/exec data :builder BUILD_SCRIPT))

(defn- execute-assemble!
  [{:keys [client runner] :as data}]
  (exec/exec data :runner (str RUNNER_WORKDIR "/assemble")))

;; ## Commit

(defn- commit!
  [{:keys [client spec runner] :as data}]
  (let [{:keys [container cmd]} runner
        {:keys [target]}  spec]
    (docker/commit-container client container target cmd)
    data))

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

(defmacro with-flow->
  [form nxt & rst]
  (if (empty? rst)
    `(let [value# ~form]
       (if-not (:error value#)
         (try
           (-> value# ~nxt)
           (catch Exception e#
             (assoc value# :error e#)))
         value#))
    `(with-flow->
       (with-flow-> ~form ~nxt)
       ~@rst)))

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
                  :env    ENV}
      (log/emph "Building image [%s] ..." :target)

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
