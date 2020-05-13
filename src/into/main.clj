(ns into.main
  (:gen-class)
  (:require [into.build :as build]
            [into.utils
             [signal :as signal]
             [task :as task]
             [version :as version]]
            [clojure.tools.logging :as log]))

;; ## CLI

(def ^:private cli-options
  [[nil "--version" "Show version information"]])

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
    :keys [show-error]}]
  (case subtask
    "build"           (build/run args)
    (show-error (str "Unknown subtask: " subtask))))

(def run
  (task/make
   {:usage "into [--version] [--help] build [<args>]"
    :cli   cli-options
    :run   (fn [opts]
             (or (print-version opts)
                 (run-subtask opts)))}))

(defn register-sigint-handler!
  []
  (signal/register-sigint-handler!))

(defn exit
  [result]
  (System/exit
    (if (or (= result :help)
            (not (:error result)))
      0
      1)))

(defn -main
  [& args]
  (register-sigint-handler!)
  (exit (run args)))
