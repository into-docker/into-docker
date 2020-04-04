(ns into.flow.transfer-sources
  (:require [into.docker :as docker]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [org.apache.commons.compress.archivers.tar
            TarArchiveEntry
            TarArchiveOutputStream]
           [java.io ByteArrayOutputStream]))

;; ## Compress

(defn- compress-sources
  [{{:keys [files]} :sources, :as data}]
  (log/debugf "[into] Creating TAR archive from %d files ..." (count files))
  (with-open [out (ByteArrayOutputStream.)
              tar (TarArchiveOutputStream. out)]
    (doseq [{:keys [file path]} files
            :let [size  (.length file)
                  entry (doto (TarArchiveEntry. path) (.setSize size))]]
      (log/debugf "[into]   Adding %s (%s bytes) ..." path size)
      (.putArchiveEntry tar entry)
      (io/copy file tar)
      (.closeArchiveEntry tar))
    (.finish tar)
    (assoc-in data [:sources :tar] (.toByteArray out))))

;; ## Copy

(defn- copy-to-container!
  [{{:keys [tar]} :sources
    {:keys [source-directory]} :paths
    :keys [client]
    :as data}
   container-key]
  (let [container (get-in data [container-key :container])]
    (with-open [in (io/input-stream tar)]
      (docker/copy-into-container!
        client
        in
        container
        source-directory)))
  data)

;; ## Cleanup

(defn- cleanup-sources
  [data]
  (-> data
      (update-in [:sources :files] #(map :path %))
      (update :sources dissoc :tar :matcher)))

;; ## Transfer

(defn transfer-sources
  [data container-key]
  (-> data
      (compress-sources)
      (copy-to-container! container-key)
      (cleanup-sources)))
