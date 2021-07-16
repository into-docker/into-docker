(ns into.build.prepare-cache
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]))

;; ## Create Cache

(defn- prepare-cache-files!
  "Copy all cache paths to `path` to prepare for extraction."
  [{:keys [builder-container cache-paths]}]
  (let [path (constants/path-for :cache-directory)
        cmd  (cache/prepare-cache-command path cache-paths)]
    (log/debug "Preparing cache paths: %s" cache-paths)
    (docker/exec-and-log builder-container {:cmd cmd})))

;; ## Flow

(defn- prepare-cache-files?
  [{:keys [spec builder-container cache-paths]}]
  (and (seq cache-paths)
       builder-container
       (or (cache/cache-file-used? spec)
           (cache/cache-volume-used? spec))))

(defn run
  [data]
  (when (prepare-cache-files? data)
    (prepare-cache-files! data))
  data)
