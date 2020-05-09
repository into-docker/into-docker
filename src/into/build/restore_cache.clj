(ns into.build.restore-cache
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.utils.cache :as cache]
            [clojure.java.io :as io]))

;; ## Cache File Injection

(defn- inject-cache-directory!
  [builder-container cache-from]
  (with-open [in (io/input-stream cache-from)]
    (docker/stream-into-container
     builder-container
     (constants/path-for :working-directory)
     in)))

(defn- restore-cache-files!
  [builder-container cache-paths]
  (let [cmd (cache/restore-cache-command
             (constants/path-for :cache-directory)
             cache-paths)]
    (docker/exec-and-log builder-container {:cmd cmd})))

(defn- restore-cache!
  [{:keys [spec builder-container cache-paths]}]
  (let [cache-from (:cache-from spec)]
    (log/info "Restoring cache from '%s' ..." cache-from)
    (inject-cache-directory! builder-container cache-from)
    (restore-cache-files! builder-container cache-paths)))

;; ## Flow

(defn- restore-cache?
  [{:keys [spec cache-paths]}]
  (and (some-> spec :cache-from io/file (.isFile))
       (seq cache-paths)))

(defn run
  [data]
  (when (restore-cache? data)
    (restore-cache! data))
  data)
