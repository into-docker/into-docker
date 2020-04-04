(ns into.main
  (:gen-class)
  (:require [peripheral.core :refer [defsystem+ with-start]]
            [into
             [docker :as docker]
             [flow :as flow]]
            [into.docker.clj-docker-client :as impl]))

;; ## System

(defsystem+ IntoDocker [uri]
  :docker [])

;; ## Helper

(defn- get-docker-uri
  []
  (or (System/getenv "DOCKER_HOST")
      "unix:///var/run/docker.sock"))

(defn -main
  [& args]
  (with-start [system (map->IntoDocker
                        {:docker (impl/make {:uri (get-docker-uri)})})]
    (-> (flow/run (:docker system)
                  {:builder "into-yarn"
                   :target "test"
                   :sources "."})
        (dissoc :client))))
