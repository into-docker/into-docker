(ns into.docker.tar
  (:require [into.log :as log]
            [clojure.java.io :as io])
  (:import [org.apache.commons.compress.archivers.tar
            TarArchiveEntry
            TarArchiveInputStream
            TarArchiveOutputStream]
           [java.io
            ByteArrayOutputStream
            File
            InputStream
            OutputStream]
           [java.util.zip
            GZIPInputStream
            GZIPOutputStream]))

;; ## Create TAR

(defn- wrap-tar-stream
  ^TarArchiveOutputStream
  [^OutputStream stream]
  (doto (TarArchiveOutputStream. stream)
    (.setLongFileMode TarArchiveOutputStream/LONGFILE_POSIX)))

(defn- add-tar-entry!
  [^TarArchiveOutputStream tar
   {:keys [source ^String path ^int length]}]
  (log/debug "|   %s (%s) ..." path (log/as-file-size length))
  (let [entry (doto (TarArchiveEntry. path)
                (.setSize length))]
    (.putArchiveEntry tar entry)
    (with-open [in (io/input-stream source)]
      (io/copy in tar :buffer-size 1024))
    (.closeArchiveEntry tar)))

(defn- write-tar!
  [^OutputStream out sources]
  (log/debug "Creating TAR archive from %d files ..." (count sources))
  (with-open [tar (wrap-tar-stream out)]
    (doseq [source sources]
      (add-tar-entry! tar source))
    (.finish tar)))

(defn tar
  "Create a byte array representing a tar archive of the given sources.
   Sources need to consist of a `:source` (anything that can be coerced via
   io/input-stream), a `:length` and a `:path` (String)."
  ^bytes [sources]
  (with-open [out (ByteArrayOutputStream.)]
    (write-tar! out sources)
    (.toByteArray out)))

(defn tar-gz
  "Create a byte array representing a tar archive of the given sources.
   Sources need to consist of a `:source` (anything that can be coerced via
   io/input-stream), a `:length` and a `:path` (String)."
  ^bytes [sources]
  (with-open [out (ByteArrayOutputStream.)
              gz  (GZIPOutputStream. out)]
    (write-tar! gz sources)
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
       (log/debug "|   %s" path)
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
     (log/debug "Extracting TAR stream to '%s' ..." (.getPath target))
     (->> (fn [file-path tar]
            (let [out (file-fn target file-path)]
              (ensure-parent! out)
              (io/copy tar out)))
          (untar* stream)))))

;; ## Helpers

(defmacro with-gz-open
  "Helper Macro to handle GZip streams, acting like 'with-open' and
   transparently uncompressing the contents."
  [[sym stream] & body]
  `(with-open [stream# ~stream
               ~sym (GZIPInputStream. stream#)]
     ~@body))
