(ns into.build.read-cache-paths
  (:require [into
             [constants :as constants]
             [docker :as docker]]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(let [prefix (io/file (constants/path-for :source-directory))]
  (defn- ->absolute-path
    [path]
    (let [f (io/file path)]
      (.getPath
       (if (.isAbsolute f)
         f
         (io/file prefix f))))))

(defn- as-path-seq
  [^bytes cache-file-contents]
  (with-open [in (io/reader cache-file-contents)]
    (->> (line-seq in)
         (map string/triml)
         (remove string/blank?)
         (remove #(string/starts-with? % "#"))
         (map ->absolute-path)
         (vec))))

(defn run
  [{:keys [builder-container] :as data}]
  (->> (constants/path-for :cache-file)
       (docker/read-file builder-container)
       (as-path-seq)
       (assoc data :cache-paths)))
