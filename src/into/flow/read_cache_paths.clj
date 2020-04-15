(ns into.flow.read-cache-paths
  (:require [into.docker :as docker]
            [into.utils
             [data :as data]]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(defn- as-path-seq
  [^bytes cache-file-contents]
  (with-open [in (io/reader cache-file-contents)]
    (->> (line-seq in)
         (remove string/blank?)
         (vec))))

(defn run
  [{:keys [client] :as data}]
  (->> (docker/read-container-file!
         client
         (data/instance-container data :builder)
         (data/path-for data :cache-file))
       (as-path-seq)
       (assoc-in data [:cache :paths])))
