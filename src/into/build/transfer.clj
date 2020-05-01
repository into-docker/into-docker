(ns into.build.transfer
  (:require [into.flow
             [core :as flow]]
            [into.docker :as docker]
            [into.utils
             [data :as data]
             [log :as log]]))

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

(defn pre-run
  "This moves the assemble script. This is done before any user-provided
   logic is called to prevent changes to the assemble script and thus retain
   the integrity of running it as root in the runner container."
  [data]
  (flow/with-flow-> data
    (log/debug "Transferring assemble script ...")
    (copy-between-containers!
     [:builder (data/path-for data :assemble-script)]
     [:runner (data/path-for data :working-directory)])))

(defn run
  [data]
  (flow/with-flow-> data
    (log/debug "Transferring sources ...")
    (copy-between-containers!
     [:builder (data/path-for data :artifact-directory)]
     [:runner (data/path-for data :working-directory)])))
