(ns into.flow.pattern
  (:require [clojure.string :as string])
  (:import [com.github.dockerjava.core GoLangFileMatch]
           [org.apache.commons.io FilenameUtils]
           [java.nio.file Path Paths Files]))

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
;; exclusions. The following is an example .dockerignore file that uses this
;; mechanism:

(defn- as-pattern
  [pattern]
  (let [pattern (string/trim pattern)]
    (when-not (string/starts-with? pattern "#")
      (-> (if (string/starts-with? pattern "!")
            {:selector :include
             :pattern  (subs pattern 1)}
            {:selector :exclude
             :pattern   pattern})
          (update :pattern #(FilenameUtils/normalize %))
          (update :pattern #(cond-> % (string/starts-with? % "/") (subs 1)))
          (update :pattern #(GoLangFileMatch/compilePattern %))))))

(defn matcher
  "Create a function that checks all `.dockerignore` patterns against a given
   string, return the last matching pattern's result."
  [pattern-strings]
  (let [patterns (vec (keep as-pattern pattern-strings))]
    (fn [name]
      (->> patterns
           (keep
             (fn [{:keys [selector pattern]}]
               (when (re-find pattern name)
                 selector)))
           (cons :include)
           (last)
           (= :include)))))
