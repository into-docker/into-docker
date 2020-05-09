(ns into.build.collect-sources
  (:require [into.utils.collect :as collect]))

(defn run
  [{:keys [spec ignore-paths] :as data}]
  (->> (collect/collect-by-patterns
        (:source-path spec)
        {:exclude ignore-paths})
       (assoc data :source-paths)))
