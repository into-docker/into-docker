(ns into.build.commit
  (:require [into.flow
             [core :as flow]]
            [into.utils
             [labels :as labels]
             [log :as log]]
            [into.docker :as docker]
            [clojure.string :as string]))

;; ## Commit

(defn- commit-container!
  [{:keys [client spec] :as data} container-key suffix]
  (let [{:keys [container cmd entrypoint]}
        (get-in data [:instances container-key])

        {:keys [full-name]}
        (:target-image spec)]
    (if entrypoint
      (log/debug data
                 "Committing image [%s] with ENTRYPOINT %s and CMD %s"
                 full-name
                 entrypoint
                 cmd)
      (log/debug data
                 "Committing image [%s] with CMD %s"
                 full-name
                 cmd))
    (->> {:image      full-name
          :cmd        cmd
          :entrypoint entrypoint
          :labels     (labels/create-labels data)}
         (docker/commit-container client container))
    data))

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/emph "Saving image [%s] ..."
              (get-in data [:spec :target-image :full-name]))
    (commit-container! :runner "")))
