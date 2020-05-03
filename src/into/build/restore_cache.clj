(ns into.build.restore-cache
  (:require [into.flow
             [core :as flow]
             [exec :as exec]]
            [into.docker :as docker]
            [into.utils
             [cache :as cache]
             [data :as data]
             [log :as log]]
            [clojure.java.io :as io]))

;; ## Cache File Injection

(defn- inject-cache-directory!
  [{:keys [client] :as data} cache-from]
  (with-open [in (io/input-stream cache-from)]
    (docker/copy-into-container!
     client
     in
     (data/instance-container data :builder)
     (data/path-for data :working-directory)))
  data)

(defn- restore-cache-files!
  [data]
  (let [cmd (cache/restore-cache-command
             (data/path-for data :cache-directory)
             (get-in data [:cache :paths]))]
    (exec/exec data :builder cmd [])))

;; ## Flow

(defn run
  [{:keys [cache spec] :as data}]
  (or (when-let [{:keys [cache-from]} (:cache-spec spec)]
        (when (seq (:paths cache))
          (when (.isFile (io/file cache-from))
            (flow/with-flow-> data
              (log/info "Restoring cache from '%s' ..." cache-from)
              (inject-cache-directory! cache-from)
              (restore-cache-files!)))))
      data))
