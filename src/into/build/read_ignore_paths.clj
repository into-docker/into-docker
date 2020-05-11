(ns into.build.read-ignore-paths
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [flow :as flow]
             [log :as log]]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.nio.file Path Paths]))

;; ## Helpers

(let [rst (into-array String [])]
  (defn- ->path
    ^Path [path]
    (Paths/get path rst)))

(defn- patterns-from
  [source]
  (with-open [in (io/reader source)]
    (->> (line-seq in)
         (map string/trim)
         (remove string/blank?)
         (remove #(string/starts-with? % "#"))
         (vec))))

(defn- add-ignore-paths
  [data source]
  (->> (patterns-from source)
       (update data :ignore-paths concat)))

(defn- add-ignore-path-if-in-directory
  [data ^String dir ^String file]
  (let [dir (->path dir)
        file (->path file)]
    (when (.startsWith file dir)
      (->> [(str (.relativize dir file))]
           (update data :ignore-paths concat)))))

;; ## Steps

(defn- add-default-ignore-paths
  [data]
  (update data
          :ignore-paths
          concat
          constants/default-ignore-paths))

(defn- add-builder-ignore-paths
  [{:keys [builder-container] :as data}]
  (let [path (constants/path-for :ignore-file)]
    (->> path
         (docker/read-file builder-container)
         (add-ignore-paths data))))

(defn- add-local-ignore-paths
  [{:keys [spec] :as data}]
  (let [f (io/file (:source-path spec) ".dockerignore")]
    (if (.isFile f)
      (add-ignore-paths data f)
      data)))

(defn- add-cache-file-path
  [{:keys [spec] :as data}]
  (or (let [{:keys [source-path cache-from]} spec]
        (when (some-> cache-from io/file .isFile)
          (add-ignore-path-if-in-directory data source-path cache-from)))
      data))

(defn- log-ignore-paths!
  [{:keys [ignore-paths] :as data}]
  (when (seq ignore-paths)
    (log/debug "Using the following ignore patterns:")
    (doseq [pattern ignore-paths]
      (log/debug "|   %s" pattern)))
  data)

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (add-default-ignore-paths)
    (add-builder-ignore-paths)
    (add-cache-file-path)
    (add-local-ignore-paths)
    (log-ignore-paths!)))
