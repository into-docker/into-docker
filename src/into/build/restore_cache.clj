(ns into.build.restore-cache
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]))

;; ## Move Cache Files to original locations

(defn- restore-cache-files!
  [{:keys [builder-container cache-paths]}]
  (log/debug "Restoring cache paths: %s" cache-paths)
  (let [cmd (cache/restore-cache-command
              (constants/path-for :cache-directory)
              cache-paths)]
    (docker/exec-and-log builder-container {:cmd cmd})))

;; ## Flow

(defn restore-cache?
  [{:keys [spec cache-paths]}]
  (and (seq cache-paths)
       (or (cache/cache-file-exists? spec)
           (cache/cache-volume-used? spec))))

(defn run
  [data]
  (when (restore-cache? data)
    (restore-cache-files! data))
  data)
