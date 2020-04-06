(ns into.flow.exec
  (:require [into.docker :as docker]
            [clojure.tools.logging :as log]))

(defn- log
  [line]
  (log/infof "[into] |   %s" line))

(defn- env->seq
  [env]
  (for [[k v] env]
    (format "%s=%s" (str k) (str v))))

(defn exec
  [{:keys [client] :as data} k cmd env]
  (let [{:keys [container image]} (get data k)]
    (log/debugf "[into] Running in (%s): %s" image cmd)
    (docker/execute-command!
     client
     container
     cmd
     (env->seq env)
     log))
  data)
