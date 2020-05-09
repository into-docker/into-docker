(ns into.build.prepare-target-image
  (:require [into.flow :as flow]
            [into.constants :as constants]
            [into.docker :as docker]))

(defn- create-target-image
  [data image-name]
  (->> (let [labels (get-in data [:builder-image :labels])
             cmd    (get labels constants/runner-cmd-label)
             entryp (get labels constants/runner-entrypoint-label)]
         (cond-> (docker/->image image-name)
           entryp (assoc :entrypoint ["sh" "-c" (str entryp " $@") "--"]
                         :cmd        [])
           cmd    (assoc :cmd        ["sh" "-c" cmd])))
       (assoc data :target-image)))

(defn run
  [data]
  (if-let [image-name (get-in data [:spec :target-image-name])]
    (flow/with-flow-> data
      (create-target-image image-name))
    data))
