(ns into.build.assemble-script
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]))

;; ## Execute

(defn- execute-assemble!
  [{:keys [runner-container runner-env]}]
  (let [spec {:cmd [(constants/path-for :assemble-script-runner)]
              :env runner-env}]
    (docker/exec-and-log runner-container spec)))

;; ## Flow

(defn run
  [{:keys [runner-container] :as data}]
  (when runner-container
    (log/emph "Assembling application in [%s] ..." runner-container)
    (execute-assemble! data))
  data)
