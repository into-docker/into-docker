(ns into.main
  (:gen-class)
  (:require [into.build :as build]
            [into.utils
             [signal :as signal]
             [task :as task]
             [version :as version]]
            [jansi-clj.core :as jansi]
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

(defn -main
  [& args]
  (signal/register-sigint-handler!)
  (let [result (run args)]
    (System/exit
     (if (or (= result :help)
             (not (:error result)))
       0
       1))))
