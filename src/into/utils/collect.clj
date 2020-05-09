(ns into.utils.collect
  (:require [into.utils.pattern :as pattern])
  (:import [java.io File]
           [java.nio.file Path Paths]))

;; ## Helpers

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
      (if (.isDirectory f)
        (recur (concat (.listFiles f) rst) files)
        (let [fp (relative-path path f)]
          (if (matcher fp)
            (recur rst (conj files fp))
            (recur rst files))))
      files)))

;; ## Collect

(defn collect-by-patterns
  "Collect all files from the given path, applying the given include and exclude
   patterns."
  [path {:keys [include exclude] :or {include ["**"]}}]
  (let [include-matcher (pattern/matcher include)
        exclude-matcher (pattern/matcher exclude)
        matcher #(and (include-matcher %) (not (exclude-matcher %)))]
    (collect-matching-files
     (->path path)
     matcher)))
