(ns into.build.cleanup
  (:require [into
             [docker :as docker]
             [log :as log]]))

;; ## Cleanup

(defn- cleanup-container!
  [data container-key]
  (if-let [container (get data container-key)]
    (do
      (log/debug "  Cleaning up container [%s] ..." container)
      (docker/cleanup-container container)
      (dissoc data container-key))
    data))

;; ## Flow

(defn run
  [data]
  (try
    (log/debug "Cleaning up resources ...")
    (-> data
        (cleanup-container! :runner-container)
        (cleanup-container! :builder-container))
    (catch Exception e
      (assoc data :cleanup-error e))))
