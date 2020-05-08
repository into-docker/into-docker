(ns into.docker.tar
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [org.apache.commons.compress.archivers.tar
            TarArchiveEntry
            TarArchiveInputStream
            TarArchiveOutputStream]
           [java.io
            ByteArrayOutputStream
            File
            InputStream
            OutputStream]))

;; ## Create TAR

(defn- wrap-tar-stream
  ^TarArchiveOutputStream
  [^OutputStream stream]
  (doto (TarArchiveOutputStream. stream)
    (.setLongFileMode TarArchiveOutputStream/LONGFILE_POSIX)))

(defn- add-tar-entry!
  [^TarArchiveOutputStream tar
   {:keys [source ^String path ^int length]}]
  (log/debugf "[into] |   %s (%s bytes) ..." path length)
  (let [entry (doto (TarArchiveEntry. path)
                (.setSize length))]
    (.putArchiveEntry tar entry)
    (with-open [in (io/input-stream source)]
      (io/copy in tar))
    (.closeArchiveEntry tar)))

(defn tar
  "Create a byte array representing a tar archive of the given sources.
   Sources need to consist of a `:source` (anything that can be coerced via
   io/input-stream), a `:length` and a `:path` (String)."
  ^bytes [sources]
  (log/debugf "[into] Creating TAR archive from %d files ..."
              (count sources))
  (with-open [out (ByteArrayOutputStream.)
              tar (wrap-tar-stream out)]
    (doseq [source sources]
      (add-tar-entry! tar source))
    (.finish tar)
    (.toByteArray out)))

;; ## Extract TAR

(defn- tar-seq
  [^TarArchiveInputStream tar]
  (when-let [entry (.getNextTarEntry tar)]
    (cons entry (lazy-seq (tar-seq tar)))))

(defn- ensure-parent!
  [^File out]
  (let [parent (.getParentFile out)]
    (when-not (.isDirectory parent)
      (.mkdirs parent))))

(defn- untar*
  ([^InputStream stream write-fn]
   (with-open [tar (TarArchiveInputStream. stream)]
     (doseq [^TarArchiveEntry e (tar-seq tar)
             :when (.isFile e)
             :let [path (.getName e)]]
       (log/debugf "[into] |   %s" path)
       (write-fn path tar)))))

(defn untar-seq
  "Create a seq with the contents of the TAR file represented by the given
   input stream. The seq will have the same format as the one expected by
   `tar`."
  [^InputStream stream]
  (let [sources (volatile! [])]
    (->> (fn [path tar]
           (with-open [out (ByteArrayOutputStream.)]
             (io/copy tar out)
             (let [data (.toByteArray out)]
               (vswap! sources conj {:source data
                                     :length (count data)
                                     :path path}))))
         (untar* stream))
    @sources))

(defn untar
  "Untar the given file to the given path. If `file-fn` is given, it will be
   called with `path` and the file's relative path and should produce a
   `java.io.File` object to extract to."
  ([^InputStream stream path]
   (untar stream path io/file))
  ([^InputStream stream path file-fn]
   (let [target (io/file path)]
     (when-not (.isDirectory target)
       (.mkdirs target))
     (log/debugf "[into] Extracting TAR stream to '%s' ..."
                 (.getPath target))
     (->> (fn [file-path tar]
            (let [out (file-fn target file-path)]
              (ensure-parent! out)
              (io/copy tar out)))
          (untar* stream)))))
