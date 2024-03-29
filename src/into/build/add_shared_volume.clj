(ns into.build.add-shared-volume
  (:require [into.constants :as constants])
  (:import [java.util UUID]))

(defn- random-name
  []
  (str "into-docker-shared-" (UUID/randomUUID)))

(defn- add-shared-volume
  [data image-key volume-name]
  (->> {:name     volume-name
        :path     (constants/path-for :artifact-directory)
        :retain?  false}
       (update-in data [image-key :volumes] conj)))

(defn- use-shared-volume?
  [{:keys [spec]}]
  (and (:target-image-name spec)
       (:use-volumes? spec)))

(defn run
  [data]
  (if (use-shared-volume? data)
    (let [shared-volume (random-name)]
      (-> data
          (add-shared-volume :builder-image shared-volume)
          (add-shared-volume :runner-image shared-volume)))
    data))
