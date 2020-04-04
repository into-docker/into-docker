(ns into.flow.exec
  (:require [into.docker :as docker]
            [jansi-clj.core :as jansi]
            [clojure.tools.logging :as log]))

(defn- log
  [line]
  (log/infof "[into] |   %s" line))

(defn exec
  [{:keys [client env] :as data} k cmd]
  (let [{:keys [container image]} (get data k)]
    (log/debugf "[into] (%s) $ %s" image cmd)
    (docker/execute-command! client container ["sh" "-c" cmd] env log))
  data)
