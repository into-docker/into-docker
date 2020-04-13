(ns into.flow.commit
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.utils.labels :as labels]
            [into.docker :as docker]
            [clojure.string :as string]))

;; ## Commit

(defn- commit-container!
  [{:keys [client spec] :as data} container-key suffix]
  (let [{:keys [container cmd]} (get-in data [:instances container-key])
        {:keys [full-name]} (:target-image spec)]
    (log/debug data
               "Committing image [%s] with CMD: %s"
               full-name
               cmd)
    (->> {:image  full-name
          :cmd    cmd
          :labels (labels/create-labels spec)}
         (docker/commit-container client container))
    data))

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/emph "Saving image [%s] ..."
              (get-in data [:spec :target-image :full-name]))
    (commit-container! :runner "")))
