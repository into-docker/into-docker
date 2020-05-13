(ns into.build.prepare-target-image
  (:require [into.flow :as flow]
            [into.constants :as constants]
            [into.docker :as docker]))

(defn- create-target-image
  [{:keys [runner-image builder-image] :as data} image-name]
  (->> (let [{:keys [labels]} builder-image
             {:keys [cmd entrypoint]} runner-image
             cmd'        (get labels constants/runner-cmd-label)
             entrypoint' (get labels constants/runner-entrypoint-label)]
         (cond-> (docker/->image image-name)
           :always     (assoc :cmd cmd, :entrypoint entrypoint)
           entrypoint' (assoc :entrypoint ["sh" "-c" (str entrypoint " $@") "--"]
                              :cmd        [])
           cmd'        (assoc :cmd        ["sh" "-c" cmd'])))
       (assoc data :target-image)))

(defn run
  [data]
  (if-let [image-name (get-in data [:spec :target-image-name])]
    (flow/with-flow-> data
      (create-target-image image-name))
    data))
