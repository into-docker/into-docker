(ns into.build.collect-sources
  (:require [into.flow :as flow]
            [into.utils.collect :as collect]))

(defn- verify-sources
  [{:keys [spec source-paths] :as data}]
  (if (empty? source-paths)
    (flow/fail data (format "No source files found in '%s'."
                            (:source-path spec)))
    data))

(defn run
  [{:keys [spec ignore-paths] :as data}]
  (->> (collect/collect-by-patterns
        (:source-path spec)
        {:exclude ignore-paths})
       (assoc data :source-paths)
       (verify-sources)))
