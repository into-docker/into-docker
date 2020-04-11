(ns into.main
  (:gen-class)
  (:require [into.flow :as flow]
            [into.docker.client :as client]
            [jansi-clj.core :as jansi]
            [peripheral.core :as p]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli])
  (:import [org.slf4j LoggerFactory]
           [ch.qos.logback.classic Level Logger]))

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
   ["-v" nil "Increase verbosity (can be used multiple times)"
    :id :verbosity
    :default 0
    :update-fn inc]
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

(defn- run-flow
  [opts]
  (let [{:keys [options arguments]} opts
        {:keys [target]} options
        [builder sources] arguments]
    (set-verbosity! options)
    (p/with-start [client (client/make {:uri (get-docker-uri)})]
      (-> (flow/run client
                    {:builder builder
                     :target  target
                     :sources sources})
          (dissoc :client)))))

(defn run
  [args]
  (let [opts (cli/parse-opts args cli-options)]
    (or (print-errors opts)
        (print-help opts)
        (run-flow opts))))

(defn -main
  [& args]
  (let [result (run args)]
    (System/exit
     (if (or (= result :help)
             (not (:error result)))
       0
       1))))
