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
  (let [{:keys [name tag]} (proto/->image image)]
    (->> {:op :ImageCreate
          :params {:fromImage name, :tag tag}}
         (docker/invoke images)
         (throw-on-error)))
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
  (container [_ container-name image]
    (container/make clients container-name image)))

;; ## Constructor

(defn make
  ([]
   (make {}))
  ([components]
   (map->DockerClient components)))

(defn start
  [{:keys [uri api-version] :as client}]
  {:pre [(seq uri)]}
  (let [conn    (docker/connect {:uri uri})
        mkcli   #(docker/client
                   {:conn        conn
                    :category    %
                    :api-version api-version})
        clients {:images     (mkcli :images)
                 :containers (mkcli :containers)
                 :commit     (mkcli :commit)
                 :volumes    (mkcli :volumes)
                 :exec       (mkcli :exec)}]
    (assoc client :conn conn, :clients clients)))
