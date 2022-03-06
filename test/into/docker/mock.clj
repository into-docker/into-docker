(ns into.docker.mock
  "Mock Docker client with easy adjustment for specific test cases."
  (:require [into.docker :as docker]
            [into.docker.tar :as tar]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io
            ByteArrayInputStream
            ByteArrayOutputStream]
           [java.nio
            ByteBuffer
            ByteOrder]))

;; ## Debug

(def ^:dynamic *debug?* false)

(defn debug
  [& args]
  (when *debug?*
    (println '---debug--- (string/join " " args))))

;; ## Exec

(defn exec-stream-block
  "Create a block as it would be returned from a docker exec stream,
   prefixed as either stdout or stderr."
  ^bytes [stream data]
  (let [^bytes data (if (string? data)
                      (.getBytes ^String data)
                      data)
        len         (count data)]
    (-> (ByteBuffer/allocate (+ len 8))
        (.put (byte (case stream :stdout 0x01 :stderr 0x02)))
        (.position 4)
        (.order ByteOrder/BIG_ENDIAN)
        (.putInt len)
        (.put data)
        (.array))))

(defn as-exec-stream
  [output]
  (with-open [out (ByteArrayOutputStream.)]
    (doseq [[stream data] output]
      (.write out (exec-stream-block stream data)))
    (ByteArrayInputStream.
      (.toByteArray out))))

(defrecord MockExec [container output result]
  docker/DockerExec
  (exec-container [_]
    container)
  (exec-stream [_]
    (as-exec-stream output))
  (exec-result [_]
    result))

(defn as-exec-result
  [container & [stdout]]
  (->MockExec
    container
    (if stdout
      [[:stdout stdout]]
      [])
    {:exit 0}))

(defn as-exec-error
  [container stderr]
  (->MockExec
    container
    [[:stderr stderr]]
    {:exit 71}))

(defn exec-collector
  []
  (let [stdout-builder (StringBuilder.)
        stderr-builder (StringBuilder.)]
    (reify Object
      clojure.lang.IFn
      (invoke [_ {:keys [stream ^String line]}]
        (-> (case stream
              :stderr stderr-builder
              :stdout stdout-builder)
            (.append line)))
      clojure.lang.IDeref
      (deref [_]
        {:stdout (.toString stdout-builder)
         :stderr (.toString stderr-builder)}))))

;; ## Filesystem

(defprotocol FileSystem
  (file-exists? [_ path])
  (list-files [_ directory-path])
  (add-file [_ path contents])
  (move-file [_ path target-path])
  (get-file-contents ^bytes [_ path]))

(let [ba (class (byte-array 0))]
  (defn- as-bytes
    ^bytes [data]
    (cond (string? data)      (.getBytes ^String data)
          (instance? ba data) data
          :else               (.getBytes (pr-str data)))))

(defn list-contents
  [fs path]
  (let [path (-> path io/file (.getPath) (str "/"))
        path-count (count path)]
    (->> (list-files fs path)
         (map #(subs % path-count)))))

(defrecord MockFileSystem [fs]
  FileSystem
  (file-exists? [_ path]
    (contains? @fs path))
  (list-files [_ directory-path]
    (let [prefix (if (= directory-path "/")
                   directory-path
                   (-> directory-path
                       (io/file)
                       (.getPath)
                       (str "/")))]
        (set
          (for [fs-path (keys @fs)
                :when (string/starts-with? fs-path prefix)]
            fs-path))))
  (add-file [this path contents]
    (when (string/starts-with? path "/")
      (debug :add-file path))
    (swap! fs assoc path (as-bytes contents))
    this)
  (move-file [this path target-path]
    (debug :move-file path target-path)
    (swap! fs #(-> %
                   (assoc target-path (get % path))
                   (dissoc path)))
    this)
  (get-file-contents [_ path]
    (when (string/starts-with? path "/")
      (debug :get-file-contents path))
    (or (get @fs path)
        (throw (IllegalStateException. "File does not exist."))))

  Object
  (toString [_]
    (pr-str @fs)))

(defn make-file-system
  []
  (->MockFileSystem (atom {})))

;; ## Container

(defn- as-tar-source
  [path data]
  {:source data
   :length (count data)
   :path   path})

(defn- file->tar-sources
  [fs path]
  [(as-tar-source
     (.getName (io/file path))
     (get-file-contents fs path))])

(defn- as-tar-entry-path
  "To replicate the behaviour of the actual `ContainerArchive` call, we
   have to include the directory name in the entry. I.e. when getting
   the directory `/tmp/artifacts`, the files will be prefixed with
   `artifacts/`."
  [path fs-path]
  (let [index (.lastIndexOf ^String path "/")]
    (subs fs-path (inc index))))

(defn- files->tar-sources
  [fs path]
  (for [fs-path (list-files fs path)]
    (as-tar-source
      (as-tar-entry-path path fs-path)
      (get-file-contents fs fs-path))))

(defn- env->map
  [env]
  (->> env
       (map #(string/split % #"=" 2))
       (into {})))

(defrecord MockContainer [fs name commit events]
  FileSystem
  (file-exists? [_ path]
    (file-exists? fs path))
  (add-file [this path contents]
    (add-file fs path contents)
    this)
  (move-file [this path target-path]
    (move-file fs path target-path)
    this)
  (list-files [_ directory-path]
    (list-files fs directory-path))
  (get-file-contents [_ path]
    (get-file-contents fs path))

  docker/DockerContainer
  (container-name [_]
    name)
  (container-user [_]
    "builder")
  (run-container [_]
    (swap! events conj :run))
  (commit-container [_ data]
    (->> (assoc data :fs (->MockFileSystem (atom @(:fs fs))))
         (reset! commit)))
  (cleanup-container [_]
    (swap! events conj :cleanup))
  (cleanup-volumes [_]
    (swap! events conj :cleanup-volumes))
  (stream-from-container [this path]
    (->> (if (file-exists? this path)
           (file->tar-sources fs path)
           (files->tar-sources fs path))
         (tar/tar)
         (io/input-stream)))
  (stream-into-container [this target-path tar-stream]
    (with-open [^java.io.InputStream in
                ;; The archive with the source files is compressed, others
                ;; are not.
                (if (= target-path "/tmp/src")
                  (java.util.zip.GZIPInputStream. tar-stream)
                  tar-stream)]
      (doseq [{:keys [source path]} (tar/untar-seq in)
              :let [full-path (.getPath (io/file target-path path))]]
        (add-file this full-path source))))
  (run-container-command [this data]
    (some-> (let [[path & args] (:cmd data)]
              (if (file-exists? this path)
                (let [f (-> (get-file-contents this path)
                            (String.)
                            (read-string)
                            (eval))
                      e (-> data
                            (assoc :container this)
                            (update :env env->map))]
                  (apply f e args))
                (->MockExec this [[:stderr (str "Not found: " path)]] {:exit 1})))
            (update :result
                    merge
                    (select-keys data [:cmd :env]))))

  Object
  (toString [_]
    name))

(defn add-event
  [container evt]
  (swap! (:events container) conj evt))

(defn events
  [container]
  @(:events container))

(defn cat-script
  [{:keys [container]} path]
  (if (file-exists? container path)
    (as-exec-result container (get-file-contents container path))
    (as-exec-error container (str "File not found: " path))))

(defn mkdir-script
  [{:keys [container]} & args]
  (add-event container (vec (list* :mkdir args)))
  (as-exec-result container))

(defn chown-script
  [{:keys [container]} & args]
  (add-event container (vec (list* :chown args)))
  (as-exec-result container))

(defn sh-script
  [{:keys [container]} & [flag cmd]]
  ;; cache scripts
  (when (= flag "-c")
    (if (string/includes? cmd "rm -rf")
      ;; restore script
      (doseq [[_ from to] (re-seq #"_CPTH_='([a-z0-9/]+)'; _PTH_='([a-z0-9/]+)'" cmd)]
        (when (file-exists? container from)
          (move-file container from to)))
      ;; prepare script
      (doseq [[_ to from] (re-seq #"_CPTH_='([a-z0-9/]+)'; _PTH_='([a-z0-9/]+)'" cmd)]
        (when (file-exists? container from)
          (move-file container from to)))))
  (as-exec-result container))

(defn container
  "Create a new mock container."
  [& [name]]
  (map->MockContainer
    {:fs     (-> (make-file-system)
                 (add-file "cat"   `cat-script)
                 (add-file "mkdir" `mkdir-script)
                 (add-file "chown" `chown-script)
                 (add-file "sh"    `sh-script))
     :name   (or name (str (java.util.UUID/randomUUID)))
     :commit (atom nil)
     :events (atom [])}))

(defn running-container
  [& [name]]
  (doto (container name)
    (docker/run-container)))

;; ## Client

(defrecord MockClient [containers images]
  docker/DockerClient
  (with-platform [this _]
    this)
  (pull-image [_ _])
  (inspect-image [_ image]
    (get images image))
  (container [_ _ image]
    (or (some containers [(:full-name image)
                          (:name image)])
        (throw
          (IllegalArgumentException.
            (str "Could not run container for image: " image))))))

(defn client
  "Create a new mock client."
  []
  (->MockClient {} {}))

(defn add-container
  [client image container]
  (assoc-in client [:containers image] container))

(defn add-image
  ([client {:keys [full-name labels cmd entrypoint]}]
   (->> {:Config {:Labels     (into {} labels)
                  :Cmd        cmd
                  :Entrypoint entrypoint}}
        (add-image client full-name)))
  ([client image data]
   (assoc-in client [:images image] data)))
