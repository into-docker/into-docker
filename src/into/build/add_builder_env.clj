(ns into.build.add-builder-env
  (:require [into.constants :as constants]))

(defn run
  [data]
  (->> {constants/source-dir-env   (constants/path-for :source-directory)
        constants/artifact-dir-env (constants/path-for :artifact-directory)}
       (map
        (fn [[k v]]
          (str k "=" v)))
       (update data :builder-env concat)))
