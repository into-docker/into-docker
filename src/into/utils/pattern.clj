(ns into.utils.pattern
  (:require [clojure.string :as string])
  (:import [com.github.dockerjava.core GoLangFileMatch]
           [java.nio.file Path]))

;; ## .dockerignore matching
;;
;; Matching is done using Go’s filepath.Match rules. A preprocessing step
;; removes leading and trailing whitespace and eliminates . and .. elements
;; using Go’s filepath.Clean. Lines that are blank after preprocessing are
;; ignored.
;;
;; Beyond Go’s filepath.Match rules, Docker also supports a special wildcard
;; string ** that matches any number of directories (including zero). For
;; example, **/*.go will exclude all files that end with .go that are found
;; in all directories, including the root of the build context.
;;
;; Lines starting with ! (exclamation mark) can be used to make exceptions to
;; exclusions.

(defn- compile-pattern
  [{:keys [pattern] :as data}]
  (when-let [re (some-> pattern
                        (Path/of (into-array String []))
                        (.normalize)
                        (str)
                        (cond-> (string/starts-with? pattern "/") (subs 1))
                        (GoLangFileMatch/compilePattern))]
    (assoc data :pattern re)))

(defn- as-pattern
  [pattern]
  (let [pattern (string/trim pattern)]
    (when-not (string/starts-with? pattern "#")
      (-> (if (string/starts-with? pattern "!")
            {:selector :include
             :pattern  (subs pattern 1)}
            {:selector :exclude
             :pattern   pattern})
          (compile-pattern)))))

(defn matcher
  "Create a function that checks all `.dockerignore` patterns against a given
   string, return the last matching pattern's result."
  [exclude-patterns]
  (let [patterns (vec (keep as-pattern exclude-patterns))]
    (fn [name]
      (-> (some
            (fn [{:keys [selector pattern]}]
              (when (re-find pattern name)
                selector))
            patterns)
          (or :include)
          (= :include)))))

((matcher ["//*.0"]) "0.0")
