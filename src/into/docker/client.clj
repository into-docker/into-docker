(ns into.docker.client
  "Docker client implementation based on clj-docker-client"
  (:require [into.docker :as proto]
            [into.docker.container :as container]
            [clj-docker-client.core :as docker]))

;; ## Helpers

(defn- throw-on-error
  [{:keys [message] :as result}]
  (if message
    (throw
     (IllegalStateException.
      (format "Operation failed: %s" message)))
    result))

(defn- invoke-pull-image
  [{{:keys [images]} :clients} image]
  (->> {:op :ImageCreate
        :params {:fromImage image}}
       (docker/invoke images)
       (throw-on-error))
  {:image image})

(defn- invoke-inspect-image
  [{{:keys [images]} :clients} image]
  (let [result (->> {:op :ImageInspect
                     :params {:name image}}
                    (docker/invoke images))]
    (when-not (:message result)
      result)))

;; ## Component

(defrecord DockerClient [uri conn clients]
  proto/DockerClient
  (pull-image [this image]
    (invoke-pull-image this image))
  (inspect-image [this image]
    (invoke-inspect-image this image))
  (container [this container-name image]
    (container/make clients container-name image)))

;; ## Constructor

(defn make
  ([]
   (make {}))
  ([components]
   (map->DockerClient components)))

(defn start
  [{:keys [uri] :as client}]
  {:pre [(seq uri)]}
  (let [conn    (docker/connect {:uri uri})
        clients {:images     (docker/client {:conn conn, :category :images})
                 :containers (docker/client {:conn conn, :category :containers})
                 :commit     (docker/client {:conn conn, :category :commit})
                 :exec       (docker/client {:conn conn, :category :exec})}]
    (assoc client :conn conn, :clients clients)))
