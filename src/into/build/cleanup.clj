(ns into.build.cleanup
  (:require [into
             [docker :as docker]
             [log :as log]]))

;; ## Cleanup

(defn- cleanup-container!
  [data container-key]
  (when-let [container (get data container-key)]
    (log/debug "  Cleaning up container [%s] ..." container)
    (docker/cleanup-container container))
  data)

(defn- cleanup-volumes!
  [data container-key]
  (when (get-in data [:spec :use-volumes?])
    (when-let [container (get data container-key)]
      (log/debug "  Cleaning up volumes used by [%s] ..." container)
      (docker/cleanup-volumes container)))
  data)

;; ## Flow

(defn run
  [data]
  (try
    (log/debug "Cleaning up resources ...")
    (-> data
        (cleanup-container! :runner-container)
        (cleanup-container! :builder-container)
        (cleanup-volumes! :builder-container))
    (catch Exception e
      (assoc data :cleanup-error e))))
