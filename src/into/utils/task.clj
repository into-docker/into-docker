(ns into.utils.task
  (:require [into.docker.client :as client]
            [peripheral.core :as p]
            [jansi-clj.core :as jansi]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli])
  (:import [org.slf4j LoggerFactory]
           [ch.qos.logback.classic Level Logger]))

;; ## Default Options

(def ^:private cli-options
  [["-v" "--verbose" "Increase verbosity (can be used multiple times)"
    :id :verbosity
    :default 0
    :update-fn inc]
   ["-h" "--help" "Show help"]])

;; ## Verbosity

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
  (cond (= 0 verbosity) nil
        (= 1 verbosity) (set-log-level! "DEBUG")
        :else (set-log-level! "TRACE")))

(defmacro ^:private with-verbosity
  [opts & body]
  `(let [opts# ~opts]
     (set-verbosity! (:options opts#))
     ~@body))

;; ## Helpers

(defn- show-help
  [usage {:keys [summary]}]
  (log/infof "Usage: %s" usage)
  (log/info "")
  (log/info summary)
  (log/info "")
  :help)

(defn- show-error
  [error]
  (log/error (jansi/red error))
  :error)

(defn- print-errors
  [_ {:keys [errors]}]
  (when (seq errors)
    (doseq [error errors]
      (show-error error))
    :error))

(defn- print-help
  [{:keys [usage no-args?]}
   {{:keys [help]} :options, args :arguments, :as opts}]
  (when (or help
            (not (or no-args? (seq args))))
    (show-help usage opts)))

(letfn [(find-option [cli k]
          (first
           (for [[_ _ _ & {:as rst} :as opt] cli
                 :when (= (:id rst) k)]
             opt)))]
  (defn- print-missing
    [{:keys [cli needs]} {:keys [options]}]
    (when-let [missing (seq (remove options needs))]
      (doseq [k missing
              :let [[short-opt long-opt] (find-option cli k)]]
        (show-error
         (str "Missing option: "
              (->> [short-opt long-opt]
                   (remove nil?)
                   (string/join "/")))))
      :missing)))

;; ## Subtask Wrapper

(defn- get-docker-uri
  []
  (or (System/getenv "DOCKER_HOST")
      "unix:///var/run/docker.sock"))

(defn make
  "Create a function that can be called with a seq of CLI arguments.

   - `:cli`:      CLI options as per 'tools.cli'.
   - `:no-args?`: Does the task support being called without arguments?
   - `:needs`:    Which CLI options are required?
   - `:usage`:    String describing the task usage.
   - `:docker?`:  Should a Docker client be created for this task?
   - `:run`:      Function to be called with parsed CLI arguments.
   "
  [{:keys [cli usage run docker?] :as task}]
  (let [cli (concat cli cli-options)]
    (fn [args]
      (let [opts (cli/parse-opts args cli :in-order true)
            opts     (merge
                      {:show-help #(show-help usage opts)
                       :show-error show-error}
                      opts)]
        (or (print-help task opts)
            (print-errors task opts)
            (print-missing task opts)
            (with-verbosity opts
              (if docker?
                (p/with-start [client (client/make {:uri (get-docker-uri)})]
                  (run (assoc opts :client client)))
                (run opts))))))))
