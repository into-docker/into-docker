(ns into.build.start
  (:require [into
             [docker :as docker]
             [flow :as flow]
             [log :as log]])
  (:import [java.util UUID]))

;; ## Startup Logic

(defn- random-container-name
  []
  (str "into-docker-" (UUID/randomUUID)))

(defn- log-run-container!
  [data {:keys [user] :as image}]
  (log/debug "  Running container [%s] as user '%s' ..." image user)
  data)

(defn- warn-container-root!
  [data image-key]
  (when-let [{:keys [user] :as image} (get data image-key)]
    (when (#{"root", "0"} user)
      (log/warn "Container [%s] is running as root!" image)))
  data)

(defn- run-container!
  [{:keys [client] :as data} container-key image]
  (let [container-name (random-container-name)
        container (docker/container client container-name image)]
    (docker/run-container container)
    (assoc data container-key container)))

(defn- start-container!
  [data image-key container-key]
  (if-let [image (get data image-key)]
    (flow/with-flow-> data
      (log-run-container! image)
      (run-container! container-key image))
    data))

;; ## Flow

(defn- log-start!
  [{:keys [builder-image runner-image] :as data}]
  (if runner-image
    (log/emph "Starting environment [%s -> %s] ..." builder-image runner-image)
    (log/emph "Starting builder [%s] ..." builder-image))
  data)

(defn run
  [data]
  (flow/with-flow-> data
    (log-start!)
    (warn-container-root! :builder-image)
    (start-container! :builder-image :builder-container)
    (start-container! :runner-image :runner-container)))
