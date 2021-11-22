(ns into.utils.task
  (:require [into.docker.client :as client]
            [into.log :refer [with-verbosity]]
            [jansi-clj.core :as jansi]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]))

;; ## Default Options

(def ^:private cli-options
  [["-v" "--verbose" "Increase verbosity (can be used multiple times)"
    :id :verbosity
    :default 0
    :update-fn inc]
   ["-h" "--help" "Show help"]])

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
   {{:keys [help version]} :options, args :arguments, :as opts}]
  (when (or help
            (not (or version no-args? (seq args))))
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
                (let [client (client/start (client/make-from-env))]
                  (run (assoc opts :client client)))
                (run opts))))))))
