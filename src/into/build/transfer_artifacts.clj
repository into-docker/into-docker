(ns into.build.transfer-artifacts
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]))

;; ## Steps

(defn- transfer-artifacts!
  [{:keys [builder-container runner-container] :as data}]
  (let [from-path (constants/path-for :artifact-directory)
        to-path (constants/path-for :working-directory)]
    (log/debug "Transferring artifacts [%s:%s -> %s:%s] ..."
               builder-container
               from-path
               runner-container
               to-path)
    (docker/transfer-between-containers
     builder-container
     runner-container
     from-path
     to-path))
  data)

;; ## Flow

(defn run
  [{:keys [runner-container] :as data}]
  (if runner-container
    (transfer-artifacts! data)
    data))
