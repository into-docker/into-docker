(ns into.utils.version
  (:require [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log]))

;; ## VCS Helper

(defn read-revision
  [{:keys [source-path] :or {source-path "."}}]
  (try
    (let [{:keys [exit out]} (sh "git" "rev-parse" "--short" "HEAD"
                                 :dir source-path)]
      (if (= exit 0)
        (string/trim out)
        ""))
    (catch Exception _
      (log/tracef "Failed to read revision from '%s'." source-path)
      "")))

;; ## Build-time constants

(defmacro ^:private read-current-revision
  "This will resolve to the current commit as a literal string that will
   persist after AOT compilation."
  []
  (read-revision {}))

(defmacro ^:private read-current-version
  "If built with Leiningen, this will resolve to a literal string containing
   the project version."
  []
  (System/getProperty "into.version"))

(defn current-version
  []
  (read-current-version))

(defn current-revision
  []
  (read-current-revision))
