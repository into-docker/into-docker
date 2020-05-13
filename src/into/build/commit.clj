(ns into.build.commit
  (:require [into
             [docker :as docker]
             [log :as log]]))

;; ## Commit

(defn- commit-container!
  [{:keys [target-image runner-container]}]
  (let [{:keys [cmd entrypoint]} target-image]
    (log/debug "Committing image [%s] with ENTRYPOINT %s and CMD %s"
               target-image
               entrypoint
               cmd)
    (docker/commit-container runner-container target-image)))

;; ## Flow

(defn run
  [{:keys [target-image] :as data}]
  (when target-image
    (log/emph "Saving image [%s] ..." target-image)
    (commit-container! data))
  data)
