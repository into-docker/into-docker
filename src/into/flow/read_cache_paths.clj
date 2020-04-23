(ns into.flow.read-cache-paths
  (:require [into.docker :as docker]
            [into.utils
             [data :as data]]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(defn- ->absolute-path
  [data path]
  (let [f (io/file path)
        prefix (io/file (data/path-for data :source-directory))]
    (.getPath
     (if (.isAbsolute f)
       f
       (io/file prefix f)))))

(defn- as-path-seq
  [data ^bytes cache-file-contents]
  (with-open [in (io/reader cache-file-contents)]
    (->> (line-seq in)
         (remove string/blank?)
         (map #(->absolute-path data %))
         (vec))))

(defn run
  [{:keys [client] :as data}]
  (->> (docker/read-container-file!
        client
        (data/instance-container data :builder)
        (data/path-for data :cache-file))
       (as-path-seq data)
       (assoc-in data [:cache :paths])))
