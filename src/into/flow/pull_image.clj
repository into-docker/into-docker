(ns into.flow.pull-image
  (:require [into.docker :as docker]
            [clojure.tools.logging :as log]))

(defn- pull-image-if-not-exists
  [{:keys [client]} image]
  (or (docker/inspect-image client image)
      (do (docker/pull-image client image)
          (docker/inspect-image client image))))

(defn pull-image
  [{:keys [client spec] :as data} k]
  (let [image            (get spec k)
        _                (log/debugf "[into]   Pulling image [%s] ..." image)
        {:keys [Config]} (pull-image-if-not-exists data image)]
    (->> {:image       image
          :image-hash  (:Image Config)
          :labels      (:Labels Config)
          :cmd         (:Cmd Config)}
         (assoc data k))))
