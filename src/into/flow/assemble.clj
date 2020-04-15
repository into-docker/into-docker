(ns into.flow.assemble
  (:require [into.flow
             [exec :as exec]
             [log :as log]]
            [into.utils.data :as data]))

;; ## Execute

(defn- execute-assemble!
  [data]
  (->> {"INTO_ARTIFACT_DIR" (data/path-for data :artifact-directory)}
       (exec/exec data :runner [(str (data/path-for data :working-directory)
                                     "/assemble")])))

;; ## Flow

(defn run
  [data]
  (-> data
      (log/emph "Assembling application in [%s] ..."
                (data/instance-image-name data :runner))
      (execute-assemble!)))
