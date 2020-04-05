(ns into.flow.collect-sources
  "Implement collection of files and matching against .dockerignore"
  (:require [into.docker :as docker]
            [into.utils.pattern :as pattern]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [java.io File]
           [java.nio.file Path Paths Files]))

;; ## File Matcher

(def ^:private default-patterns
  [".git/"
   ".gitignore"
   ".hg/"
   ".hgignore"
   "Dockerfile"
   ".dockerignore"])

(defn- patterns-from
  [source]
  (with-open [in (io/reader source)]
    (->> (line-seq in)
         (map string/trim)
         (remove string/blank?)
         (vec))))

(defn- patterns-from-file
  [^File file]
  (when (.isFile file)
    (patterns-from file)))

(defn- log-patterns
  [patterns]
  (->> patterns
       (map #(str "[into]   " %))
       (string/join "\n")
       (log/tracef "[into] Ignoring the following %d patterns:%n%s" (count patterns)))
  patterns)

(defn- ignore-file-matcher
  "Create a function that takes a _relative_ path and returns a boolean value
   indicating whether the given file should be included."
  [ignore-files]
  ;; DO NOT deduplicate. .dockerignore should allow explicitly including files
  ;; that were previously excluded.
  (->> (mapcat patterns-from ignore-files)
       (concat default-patterns)
       (log-patterns)
       (pattern/matcher)))

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

;; ## Collect Sources

(defn- read-container-ignore-file!
  [{:keys [client builder]}]
  (docker/read-container-file!
    client
    (:container builder)
    "/into/ignore"))

(defn- maybe-file
  [& args]
  (let [f (apply io/file args)]
    (if (.isFile f)
      f
      (byte-array 0))))

(defn collect-sources
  "Collect and attach all sources, taking into account the files that should
   be ignored via:

   - `.dockerignore`
   - `/into/ignore` in the builder container

   Matching will be done on the path relative to the source directory."
  [{{:keys [sources ignore-file]
     :or {ignore-file ".dockerignore"}} :spec
    :as data}]
  (->> [(read-container-ignore-file! data)
        (maybe-file sources ignore-file)]
       (ignore-file-matcher)
       (collect-matching-files (->path sources))
       (assoc-in data [:sources :files])))
