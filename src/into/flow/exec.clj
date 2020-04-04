(ns into.flow.exec
  (:require [into.docker :as docker]
            [clojure.tools.logging :as log]))

(defn- log
  [image line]
  (log/infof "[%s] >   %s" image line))

(defn exec
  [{:keys [client env] :as data} k cmd]
  (let [{:keys [image container]} (get data k)
        log-fn #(log image %)]
    (log/debugf "[%s] Command: %s" image cmd)
    (docker/execute-command! client container ["sh" "-c" cmd] env log-fn))
  data)
