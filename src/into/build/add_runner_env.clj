(ns into.build.add-runner-env
  (:require [into.constants :as constants]))

(defn run
  [{:keys [runner-container] :as data}]
  (if runner-container
    (->> {constants/artifact-dir-env (constants/path-for :artifact-directory)}
         (map
          (fn [[k v]]
            (str k "=" v)))
         (update data :runner-env concat))
    data))
