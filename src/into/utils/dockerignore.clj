(ns into.utils.dockerignore
  (:require [into.utils.pattern :as pattern]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(def ^:private default-patterns
  ["# --- default patterns"
   "Dockerfile"
   ".dockerignore"])

(defn- patterns-from
  [{:keys [path in]}]
  (with-open [in (io/reader in)]
    (->> (line-seq in)
         (map string/trim)
         (remove string/blank?)
         (cons (str "# --- " path))
         (vec))))

(defn- log-patterns
  [patterns]
  (->> (concat patterns ["# ---"])
       (map #(str "[into]   " %))
       (string/join "\n")
       (log/debugf "[into] Using the following ignore patterns:%n%s"))
  patterns)

(defn matcher
  "Create a function that takes a _relative_ path and returns a boolean value
   indicating whether the given file should be included."
  [ignore-files]
  ;; DO NOT deduplicate. .dockerignore should allow explicitly including files
  ;; that were previously excluded.
  (->> (mapcat patterns-from ignore-files)
       (concat default-patterns)
       (log-patterns)
       (pattern/matcher)
       (complement)))
