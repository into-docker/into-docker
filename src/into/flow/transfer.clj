(ns into.flow.transfer
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.docker :as docker]
            [into.utils.data :as data]))

;; ## Transfer

(defn- copy-between-containers!
  [{:keys [client] :as data} [from from-path] [to to-path]]
  (log/debug data
             "  Copying [%s:%s] to [%s:%s] ..."
             (name from)
             from-path
             (name to)
             to-path)
  (docker/copy-between-containers!
   client
   (data/instance-container data from)
   (data/instance-container data to)
   from-path
   to-path)
  data)

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/debug "Transferring sources ...")
    (copy-between-containers!
     [:builder (data/path-for data :assemble-script)]
     [:runner (data/path-for data :working-directory)])
    (copy-between-containers!
     [:builder (data/path-for data :artifact-directory)]
     [:runner (data/path-for data :working-directory)])))
