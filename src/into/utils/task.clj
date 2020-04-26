(ns into.utils.task
  (:require [into.docker.client :as client]
            [peripheral.core :as p]
            [jansi-clj.core :as jansi]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]))

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
  [{:keys [errors arguments] :as opts}]
  (when (seq errors)
    (doseq [error errors]
      (show-error error))
    :error))

(defn- print-help
  [usage {{:keys [help]} :options, :as opts}]
  (when help
    (show-help usage opts)))

;; ## Subtask Wrapper

(defn- get-docker-uri
  []
  (or (System/getenv "DOCKER_HOST")
      "unix:///var/run/docker.sock"))

(defn make
  [{:keys [cli usage run docker?]}]
  (fn [args]
    (let [opts (cli/parse-opts args cli :in-order true)
          opts     (merge
                     {:show-help #(show-help usage opts)
                      :show-error show-error}
                     opts)]
      (or (print-help usage opts)
          (print-errors opts)
          (if docker?
            (p/with-start [client (client/make {:uri (get-docker-uri)})]
              (run (assoc opts :client client)))
            (run opts))))))
