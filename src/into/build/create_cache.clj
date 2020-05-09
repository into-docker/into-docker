(ns into.build.create-cache
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]
            [clojure.java.io :as io])
  (:import java.util.zip.GZIPOutputStream))

;; ## Create Cache

(defn- prepare-cache-files!
  "Copy all cache paths to `path` to prepare for extraction."
  [builder-container path cache-paths]
  (let [cmd (cache/prepare-cache-command path cache-paths)]
    (docker/exec-and-log builder-container {:cmd cmd})))

(defn- export-cache-directory!
  [builder-container path cache-to]
  (with-open [out    (io/output-stream cache-to)
              gz-out (GZIPOutputStream. out)
              in     (docker/stream-from-container builder-container path)]
    (io/copy in gz-out)))

(defn- create-cache!
  [{:keys [spec builder-container cache-paths]}]
  (let [cache-to (:cache-to spec)
        path     (constants/path-for :cache-directory)]
    (log/info "Writing cache to '%s' ..." cache-to)
    (prepare-cache-files! builder-container path cache-paths)
    (export-cache-directory! builder-container path cache-to)))

;; ## Flow

(defn- create-cache?
  [{:keys [spec cache-paths]}]
  (and (:cache-to spec) (seq cache-paths)))

(defn run
  [data]
  (when (create-cache? data)
    (create-cache! data))
  data)
