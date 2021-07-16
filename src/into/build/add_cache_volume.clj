(ns into.build.add-cache-volume
  (:require [into.constants :as constants]
            [into.log :as log]
            [into.utils.sha1 :refer [sha1]]
            [clojure.java.io :as io]))

(defn- cache-volume-name
  [{{:keys [source-path]} :spec
    {:keys [full-name]} :builder-image}]
  (let [abs-path (.getAbsolutePath (io/file source-path))]
    (->> (str full-name "~" abs-path)
         (sha1)
         (str "into-docker-cache-"))))

(defn- add-cache-volume
  [data]
  (let [volume-name (cache-volume-name data)]
    (log/debug "Using cache volume: %s" volume-name)
    (->> {:name    volume-name
          :path    (constants/path-for :cache-directory)
          :retain? true}
         (update-in data [:builder-image :volumes] conj))))

(defn- use-cache-volume?
  [{:keys [spec]}]
  (and (:use-cache-volume? spec)
       (:use-volumes? spec)))

(defn run
  [data]
  (if (use-cache-volume? data)
    (add-cache-volume data)
    data))
