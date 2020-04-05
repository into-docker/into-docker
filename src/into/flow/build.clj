(ns into.flow.build
  (:require [into.flow
             [core :as flow]
             [exec :as exec]
             [log :as log]]
            [into.docker :as docker]
            [into.utils.data :as data]
            [clojure.java.io :as io])
  (:import [org.apache.commons.compress.archivers.tar
            TarArchiveEntry
            TarArchiveOutputStream]
           [java.io ByteArrayOutputStream File]))

;; ## Compress

(defn- create-source-tar!
  [{:keys [sources] :as data}]
  (log/debug data "Creating TAR archive from %d files ..." (count sources))
  (with-open [out (ByteArrayOutputStream.)
              tar (doto (TarArchiveOutputStream. out)
                    (.setLongFileMode TarArchiveOutputStream/LONGFILE_POSIX))]
    (doseq [{:keys [^File file ^String path]} sources
            :let [size  (.length file)
                  entry (doto (TarArchiveEntry. path) (.setSize size))]]
      (log/debug data "  Adding %s (%s bytes) ..." path size)
      (.putArchiveEntry tar entry)
      (io/copy file tar)
      (.closeArchiveEntry tar))
    (.finish tar)
    (.toByteArray out)))

;; ## Copy

(defn- copy-to-builder!
  [{:keys [client] :as data} ^bytes tar]
  (log/debug data
             "Injecting TAR (%d bytes) into container ..."
             (count tar))
  (let [container (data/instance-container data :builder)]
    (with-open [in (io/input-stream tar)]
      (docker/copy-into-container!
       client
       in
       container
       (data/path-for data :source-directory))))
  data)

;; ## Execute

(defn- execute-build!
  [data]
  (->> {"INTO_SOURCE_DIR"   (data/path-for data :source-directory)
        "INTO_ARTIFACT_DIR" (data/path-for data :artifact-directory)}
       (exec/exec data :builder [(data/path-for data :build-script)])))

;; ## Flow

(defn run
  [data]
  (log/emph data
            "Building artifacts in [%s] ..."
            (data/instance-image-name data :builder))
  (let [tar (create-source-tar! data)]
    (flow/with-flow-> data
      (copy-to-builder! tar)
      (execute-build!))))
