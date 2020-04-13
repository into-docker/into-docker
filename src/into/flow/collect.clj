(ns into.flow.collect
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.docker :as docker]
            [into.utils
             [data :as data]
             [dockerignore :as dockerignore]]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Path Paths Files]))

;; ## Collect Files

(let [rst (into-array String [])]
  (defn- ->path
    ^Path [path]
    (Paths/get path rst)))

(defn- relative-path
  [^Path path ^File file]
  (->> (.toPath file)
       (.relativize path)
       (str)))

(defn- collect-matching-files
  [^Path path matcher]
  (loop [[^File f & rst] (seq (.listFiles (.toFile path)))
         files      []]
    (if f
      (let [fp (relative-path path f)]
        (if (matcher fp)
          (if (.isDirectory f)
            (recur (concat (.listFiles f) rst) files)
            (recur rst (conj files {:file f, :path fp})))
          (recur rst files)))
      files)))

;; ## Build File Matcher

(defn- read-container-ignore-file!
  [{:keys [client] :as data}]
  (let [path (data/path-for data :ignore-file)]
    {:in (docker/read-container-file!
          client
          (data/instance-container data :builder)
          path)
     :path path}))

(defn- maybe-file
  [& args]
  (let [f ^File (apply io/file args)]
    {:in (if (.isFile ^File f)
           f
           (byte-array 0))
     :path (.getPath f)}))

(defn- read-local-ignore-file!
  [data]
  (maybe-file
   (get-in data [:spec :source-path])
   ".dockerignore"))

(defn- source-directory-as-path
  [{:keys [spec]}]
  (->path (:source-path spec)))

(defn- build-file-matcher
  [data]
  (->> [(read-container-ignore-file! data)
        (read-local-ignore-file! data)]
       (dockerignore/matcher)))

;; ## Flow

(defn run
  "Collect and attach all sources, taking into account the files that should
   be ignored via:

   - `.dockerignore`
   - `/into/ignore` in the builder container

   Matching will be done on the path relative to the source directory."
  [data]
  (log/debug data
             "Collecting sources in '%s' ..."
             (get-in data [:spec :source-path]))
  (flow/with-flow-> data
    (assoc :sources
           (collect-matching-files
             (source-directory-as-path data)
             (build-file-matcher data)))
    (flow/validate [:sources] seq "No source files found.")))
