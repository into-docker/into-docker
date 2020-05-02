(ns into.build.cleanup
  (:require [into.flow
             [core :as flow]]
            [into.utils
             [log :as log]]
            [into.docker :as docker]))

;; ## Cleanup

(defn- cleanup-container!
  [{:keys [client] :as data} instance-key]
  (or (when-let [{:keys [name image container]} (get-in data [:instances instance-key])]
        (when container
          (log/debug data "  Cleaning up container [%s] ..." (:full-name image))
          (docker/cleanup-container client container)
          (update-in data [:instances instance-key] dissoc :container)))
      data))

;; ## Flow

(defn run
  [data]
  (-> (try
        (-> data
            (log/info "Cleaning up resources ...")
            (cleanup-container! :runner)
            (cleanup-container! :builder))
        (catch Exception e
          (assoc data :cleanup-error e)))
      (log/report-errors)))
