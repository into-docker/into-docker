(ns into.build.inject-sources
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.docker.tar :as tar]
            [clojure.java.io :as io]))

;; ## Steps

(defn- create-sources
  ^bytes [{:keys [spec source-paths]}]
  (let [base-path (io/file (:source-path spec))]
    (for [path source-paths
          :let [file (io/file base-path path)]]
      {:source file
       :length (.length file)
       :path   path})))

(defn- inject-into-builder
  [{:keys [builder-container]} tar]
  (with-open [in (io/input-stream tar)]
    (docker/stream-into-container
     builder-container
     (constants/path-for :source-directory)
     in)))

(defn- log
  [^bytes tar]
  (log/debug "Injecting TAR archive (%s) ..."
             (log/as-file-size (count tar)))
  tar)

;; ## Flow

(defn run
  [data]
  (->> (create-sources data)
       (tar/tar-gz)
       (log)
       (inject-into-builder data))
  data)

