(ns into.flow.exec
  (:require [into.docker :as docker]
            [into.utils.data :as data]
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

(defn- handle-result
  [data full-image-name {:keys [exit cmd env]}]
  (if (not= exit 0)
    (->> (IllegalStateException.
          (format
           (str "Exec in container (%s) failed!%n"
                "  Exit Code: %d%n"
                "  Command:   %s%n"
                "  Env:       %s")
           full-image-name
           exit
           cmd
           (vec env)))
         (assoc data :error))
    data))

(defn exec
  [{:keys [client] :as data} instance-key cmd env']
  (let [{:keys [full-name env]} (data/instance-image data instance-key)]
    (log/debugf "[into] Running in (%s): %s" full-name cmd)
    (->> (docker/execute-command!
          client
          (data/instance-container data instance-key)
          {:cmd cmd
           :env (concat (env->seq env') env)}
          log)
         (handle-result data full-name))))
