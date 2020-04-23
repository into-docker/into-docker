(ns into.main
  (:gen-class)
  (:require [into.flow :as flow]
            [into.docker.client :as client]
            [into.utils
             [cache :as cache]
             [data :as data]
             [signal :as signal]
             [version :as version]]
            [jansi-clj.core :as jansi]
            [peripheral.core :as p]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli])
  (:import [org.slf4j LoggerFactory]
           [ch.qos.logback.classic Level Logger]))

;; ## Constants

(let [working-directory "/tmp"]
  (def ^:private +well-known-paths+
    {:source-directory   (str working-directory "/src")
     :artifact-directory (str working-directory "/artifacts")
     :cache-directory    (str working-directory "/cache")
     :working-directory  working-directory

     :build-script       "/into/bin/build"
     :assemble-script    "/into/bin/assemble"

     :cache-file         "/into/cache"
     :ignore-file        "/into/ignore"}))

;; ## Log Level

(defn- root-logger
  ^Logger []
  (LoggerFactory/getLogger "root"))

(defn- set-log-level!
  [^String value]
  (let [^Level level (-> value
                         (.toUpperCase)
                         (Level/valueOf))]
    (.setLevel (root-logger) level)))

(defn set-verbosity!
  [{:keys [^long verbosity]}]
  (->> (if (> verbosity 2)
         "TRACE"
         (get ["INFO" "DEBUG" "TRACE"] verbosity))
       (set-log-level!)))

;; ## Helper

(defn- get-docker-uri
  []
  (or (System/getenv "DOCKER_HOST")
      "unix:///var/run/docker.sock"))

;; ## CLI

(def ^:private cli-options
  [["-t" "--tag REPOSITORY:TAG"  "Tag to use for the output image"
    :id :target
    :parse-fn (fn [^String value]
                (if-not (string/blank? value)
                  (let [index (.lastIndexOf value ":")]
                    (if (neg? index)
                      (str value ":latest")
                      value))
                  value))
    :validate [#(not (string/blank? %)) "Cannot be blank."]]
   [nil "--auto-cache" "Use and create a file in `$HOME/.cache/into-docker` for incremental builds."
    :id :auto-cache
    :default false]
   [nil "--cache PATH" "Use and create the specified cache file for incremental builds."
    :id :cache-file]
   ["-v" nil "Increase verbosity (can be used multiple times)"
    :id :verbosity
    :default 0
    :update-fn inc]
   [nil "--version" "Show version information"]
   ["-h" "--help" "Show help"]])

;; ## Main

(defn- log-help
  [{:keys [summary]}]
  (log/info "Usage: into -t <repository>:<tag> <builder> <directory>")
  (log/info "")
  (log/info summary)
  (log/info ""))

(defn- print-errors
  [{:keys [errors arguments] :as opts}]
  (or (when (seq errors)
        (doseq [error errors]
          (log/error (jansi/red error)))
        :error)
      (when (not= (count arguments) 2)
        (log-help opts)
        :error)))

(defn- print-help
  [{{:keys [help]} :options, :as opts}]
  (when help
    (log-help opts)
    :help))

(defn- print-version
  [{{:keys [version]} :options}]
  (when version
    (log/infof "into %s (revision %s)"
               (version/current-version)
               (version/current-revision))
    :version))

(defn- build-flow-spec
  [{:keys [target auto-cache cache-file]}
   [builder-image source-path]]
  (cond->
    {:builder-image (data/->image builder-image)
     :target-image  (data/->image target)
     :source-path   source-path}

    auto-cache
    (assoc :cache-spec
           (let [cache-file (cache/default-cache-file target)]
             {:cache-from cache-file
              :cache-to   cache-file}))

    cache-file
    (assoc :cache-spec
           {:cache-from cache-file
            :cache-to   cache-file})))

(defn- run-flow
  [opts]
  (let [{:keys [options arguments]} opts]
    (set-verbosity! options)
    (p/with-start [client (client/make {:uri (get-docker-uri)})]
      (-> (flow/run
           {:client           client
            :spec             (build-flow-spec options arguments)
            :well-known-paths +well-known-paths+})
          (dissoc :client)))))

(defn run
  [args]
  (let [opts (cli/parse-opts args cli-options)]
    (or (print-help opts)
        (print-version opts)
        (print-errors opts)
        (run-flow opts))))

(defn -main
  [& args]
  (signal/register-sigint-handler!)
  (let [result (run args)]
    (System/exit
     (if (or (= result :help)
             (not (:error result)))
       0
       1))))
