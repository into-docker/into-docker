(ns into.flow.exec
  (:require [into.docker :as docker]
            [jansi-clj.core :as jansi]
            [clojure.tools.logging :as log]))

(defn- log
  [{:keys [stream line]}]
  (log/infof "[into] |   %s" (case stream
                               :stderr (jansi/fg-bright :red line)
                               line)))

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
