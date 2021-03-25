(ns into.build.build-script
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]))

;; ## Execute

(defn- execute-build!
  [{:keys [builder-container builder-env]}]
  (let [spec {:cmd [(constants/path-for :build-script)]
              :env builder-env}]
    (docker/exec-and-log builder-container spec)))

;; ## Flow

(defn run
  [{:keys [builder-container] :as data}]
  (log/emph "Running build in [%s] ..." builder-container)
  (execute-build! data)
  data)
