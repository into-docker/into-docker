(ns into.main
  (:gen-class)
  (:require [into.build :as build]
            [into.utils
             [signal :as signal]
             [task :as task]
             [version :as version]]
            [jansi-clj.core :as jansi]
            [clojure.tools.logging :as log])
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

(defn- set-verbosity!
  [{:keys [^long verbosity]}]
  (->> (if (> verbosity 2)
         "TRACE"
         (get ["INFO" "DEBUG" "TRACE"] verbosity))
       (set-log-level!)))

(defmacro ^:private with-verbosity
  [opts & body]
  `(let [logger# (root-logger)
         level#  (.getLevel logger#)
         opts#   ~opts]
     (set-verbosity! (:options opts#))
     (try
       (do ~@body)
       (finally
         (.setLevel logger# level#)))))

;; ## CLI

(def ^:private cli-options
  [["-v" "--verbose" "Increase verbosity (can be used multiple times)"
    :id :verbosity
    :default 0
    :update-fn inc]
   [nil "--version" "Show version information"]
   ["-h" "--help" "Show help"]])

;; ## Main

(defn- print-version
  [{{:keys [version]} :options}]
  (when version
    (log/infof "into %s (revision %s)"
               (version/current-version)
               (version/current-revision))
    :version))

(defn- run-subtask
  [{[subtask & args] :arguments
    :keys [show-help show-error]}]
  (cond (not subtask)       (show-help)
        (= subtask "build") (build/subtask args)
        :else               (show-error (str "Unknown subtask: " subtask))))

(def run
  (task/make
    {:usage "into [--version] [--help] [-v] build [<args>]"
     :cli   cli-options
     :run   (fn [opts]
              (with-verbosity opts
                (or (print-version opts)
                    (run-subtask opts))))}))

(defn -main
  [& args]
  (signal/register-sigint-handler!)
  (let [result (run args)]
    (System/exit
     (if (or (= result :help)
             (not (:error result)))
       0
       1))))
