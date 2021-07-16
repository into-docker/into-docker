(ns into.build.inject-cache-file
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]
            [clojure.java.io :as io]))

;; ## Cache File Injection

(defn- inject-cache-file!
  [{:keys [spec builder-container]}]
  (let [cache-from (:cache-from spec)]
    (log/debug "Injecting contents of cache archive: %s" cache-from)
    (with-open [in (io/input-stream cache-from)]
      (docker/stream-into-container
        builder-container
        (constants/path-for :working-directory)
        in))))

;; ## Flow

(defn- inject-cache-file?
  [{:keys [spec builder-container cache-paths]}]
  (and (seq cache-paths)
       builder-container
       (cache/cache-file-exists? spec)))

(defn run
  [data]
  (when (inject-cache-file? data)
    (inject-cache-file! data))
  data)
