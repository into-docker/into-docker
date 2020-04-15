(ns into.flow.start
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.docker :as docker]
            [into.utils.data :as data])
  (:import [java.util UUID]))

;; ## Startup Logic

(defn- create-working-directories!
  [{:keys [client] :as data} container]
  (docker/mkdir
    client
    container
    (data/path-for data :source-directory)
    (data/path-for data :artifact-directory)))

(defn- random-container-name
  []
  (str "into-docker-" (UUID/randomUUID)))

(defn- run-container!
  [{:keys [client] :as data} {:keys [full-name]}]
  (let [name  (random-container-name)]
    (log/debug data "  Running container [%s] ..." full-name)
    (docker/run-container client name full-name)))

(defn- start-image-instance!
  [data instance-key]
  (let [image     (data/instance-image data instance-key)
        container (run-container! data image)]
    (create-working-directories! data container)
    (data/assoc-instance-container data instance-key container)))

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/info "Starting environment [%s -> %s] ..."
              (data/instance-image-name data :builder)
              (data/instance-image-name data :runner))
    (start-image-instance! :builder)
    (start-image-instance! :runner)))
