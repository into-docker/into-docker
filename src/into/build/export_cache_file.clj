(ns into.build.export-cache-file
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]
            [clojure.java.io :as io])
  (:import java.util.zip.GZIPOutputStream))

(defn- export-cache-directory!
  [builder-container path cache-to]
  (with-open [out    (io/output-stream cache-to)
              gz-out (GZIPOutputStream. out)
              in     (docker/stream-from-container builder-container path)]
    (io/copy in gz-out)))

(defn- export-file-cache!
  [{:keys [spec builder-container]}]
  (let [cache-to (:cache-to spec)
        path     (constants/path-for :cache-directory)]
    (log/info "Writing cache to '%s' ..." cache-to)
    (export-cache-directory! builder-container path cache-to)))

(defn- export-file-cache?
  [{:keys [spec cache-paths]}]
  (and (seq cache-paths)
       (cache/cache-file-used? spec)))

(defn run
  [data]
  (when (export-file-cache? data)
    (export-file-cache! data))
  data)
