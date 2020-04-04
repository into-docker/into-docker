(ns into.flow.commit
  (:require [into.docker :as docker]
            [clojure.tools.logging :as log]))

(defn- add-target-suffix
  [target suffix]
  (let [index (.lastIndexOf target ":")]
    (if (neg? index)
      (str target ":latest" suffix)
      (str target suffix))))

(defn commit-container
  [{{:keys [target]} :spec
    client :client
    :as data} container-key suffix]
  (let [{:keys [container cmd]} (get data container-key)
        target-with-suffix (add-target-suffix target suffix)]
    (log/debugf "[into]   Committing image [%s]"
                target-with-suffix)
    data))
