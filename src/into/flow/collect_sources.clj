(ns into.flow.collect-sources
  "Implement collection of files and matching against .dockerignore"
  (:require [into.docker :as docker]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [com.github.dockerjava.core
            GoLangFileMatch]
           [java.io File]
           [java.nio.file Path Paths Files]))

;; ## File Matcher

(defn- patterns-from
  [source]
  (with-open [in (io/reader source)]
    (->> (line-seq in)
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
       (log/debugf "[into] Ignoring the following patterns:%n%s"))
  patterns)

(defn- golang-file-matcher
  "The GoLangFileMatch class returns a list of matching patterns. This means
   that we only include a file if there is no matching pattern."
  [patterns]
  (comp empty?  #(GoLangFileMatch/match patterns ^String %)))

(defn- ignore-file-matcher
  "Create a function that takes a _relative_ path and returns a boolean value
   indicating whether the given file should be included."
  [ignore-files]
  (->> (mapcat patterns-from ignore-files)
       (log-patterns)
       (golang-file-matcher)))

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
  (->> [(maybe-file sources ignore-file)
        (read-container-ignore-file! data)]
       (ignore-file-matcher)
       (collect-matching-files (->path sources))
       (assoc-in data [:sources :files])))
