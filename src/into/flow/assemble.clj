(ns into.flow.assemble
  (:require [into.flow
             [exec :as exec]
             [log :as log]]))

;; ## Execute

(defn- execute-assemble!
  [{{:keys [working-directory artifact-directory]} :paths,
    :as data}]
  (->> {"INTO_ARTIFACT_DIR" artifact-directory}
       (exec/exec data :runner [(str working-directory "/assemble")])))

;; ## Flow

(defn run
  [data]
  (-> data
      (log/emph "Assembling application in [%s] ..."
                (get-in data [:instances :runner :image :full-name]))
      (execute-assemble!)))
