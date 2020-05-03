(ns into.build.create-cache
  (:require [into.flow
             [core :as flow]
             [exec :as exec]]
            [into.docker :as docker]
            [into.utils
             [cache :as cache]
             [data :as data]
             [log :as log]]
            [clojure.java.io :as io])
  (:import java.util.zip.GZIPOutputStream))

;; ## Create Cache

(defn- prepare-cache-files!
  [data]
  (let [cmd (cache/prepare-cache-command
             (data/path-for data :cache-directory)
             (get-in data [:cache :paths]))]
    (exec/exec data :builder cmd [])))

(defn- export-cache-directory!
  [{:keys [client] :as data} cache-to]
  (with-open [out (io/output-stream cache-to)
              gz-out (GZIPOutputStream. out)]
    (docker/copy-from-container!
     client
     gz-out
     (data/instance-container data :builder)
     (data/path-for data :cache-directory)))
  data)

;; ## Flow

(defn run
  [{:keys [cache spec] :as data}]
  (or (when-let [{:keys [cache-to]} (:cache-spec spec)]
        (when (seq (:paths cache))
          (flow/with-flow-> data
            (log/info "Creating cache at '%s' ..." cache-to)
            (prepare-cache-files!)
            (export-cache-directory! cache-to))))
      data))
