(ns into.flow.containers
  (:require [into.docker :as docker]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]))

(defn- random-container-name
  [k]
  (str (name k) "-" (UUID/randomUUID)))

(defn cp
  [{:keys [client] :as data} [from from-path] [to to-path]]
  (log/debugf "[into]   Copying [%s:%s] to [%s:%s] ..."
              (name from)
              from-path
              (name to)
              to-path)
  (docker/copy-between-containers!
   client
   (get-in data [from :container])
   (get-in data [to :container])
   from-path
   to-path)
  data)

(defn run
  [{:keys [client]
    {:keys [source-directory artifact-directory]} :paths
    :as data} k]
  (let [name  (random-container-name k)
        image (get-in data [k :image])]
    (log/debugf "[into]   Running container [%s] ..." image)
    (let [container (docker/run-container client name image)]
      (docker/mkdir client container source-directory artifact-directory)
      (->> {:container container
            :name      name}
           (update data k merge)))))

(defn cleanup
  [{:keys [client] :as data} k]
  (when-let [{:keys [name image container]} (get data k)]
    (when container
      (log/debugf "[into]   Cleaning up container [%s] ..." image)
      (docker/cleanup-container client container)))
  (update data k dissoc :container :name))
