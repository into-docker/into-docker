(ns into.docker
  (:require [into.log :as log]
            [into.docker.streams :as streams]
            [potemkin :refer [defprotocol+]]
            [clojure.string :as string]))

;; ## Execution Result

(defprotocol+ DockerExec
  (exec-container [this])
  (exec-stream
   ^java.io.InputStream
   [this])
  (exec-result [this]))

(defn- handle-exec-result
  [^into.docker.DockerExec exec]
  (let [{:keys [exit cmd env] :as result} (exec-result exec)
        container (exec-container exec)]
    (when-not (= exit 0)
      (throw
       (IllegalStateException.
        (format
         (str "Exec in container (%s) failed!%n"
              "  Exit Code: %d%n"
              "  Command:   %s%n"
              "  Env:       %s")
         container
         exit
         cmd
         (vec env)))))
    result))

(defn wait-for-exec
  [^into.docker.DockerExec exec]
  (with-open [stream (exec-stream exec)]
    (let [buf (byte-array 512)]
      (while (not (neg? (.read stream buf)))))
    (handle-exec-result exec)))

(defn read-exec-stream
  ^bytes [stream-key ^into.docker.DockerExec exec]
  (with-open [stream (exec-stream exec)]
    (streams/exec-bytes stream stream-key)))

(defn read-exec-stdout
  ^String [exec]
  (String. (read-exec-stream :stdout exec) "UTF-8"))

;; ## Container Facade

(defprotocol+ DockerContainer
  (container-name [this])
  (container-user [this])
  (run-container [this])
  (commit-container [this target-image])
  (cleanup-container [this])
  (cleanup-volumes [this])
  (stream-from-container
   ^java.io.InputStream
   [this path])
  (stream-into-container [this path tar-stream])
  (run-container-command
   ^into.docker.DockerExec
   [this data]))

(defn exec-and-log
  ([container data]
   (exec-and-log container data log/report-exec))
  ([container data log-fn]
   (let [exec (run-container-command container data)]
     (with-open [stream (exec-stream exec)]
       (doseq [e (streams/log-seq stream)]
         (log-fn (update e :line string/trimr)))
       (handle-exec-result exec)))))

(defn exec-and-wait
  [container data]
  (-> (run-container-command container data)
      (wait-for-exec)))

(defn mkdir
  [container path & more]
  (->> {:cmd `["mkdir" "-p" ~path ~@more]
        :root? true}
       (exec-and-log container)))

(defn chown
  [container path & more]
  (->> {:cmd `["chown" "-R" ~(container-user container) ~path ~@more]
        :root? true}
       (exec-and-log container)))

(defn read-file
  ^bytes [container path]
  (->> {:cmd ["cat" path]}
       (run-container-command container)
       (read-exec-stream :stdout)))

(defn transfer-between-containers
  [from-container to-container from-path to-path]
  (with-open [in (stream-from-container from-container from-path)]
    (stream-into-container to-container to-path in)))

;; ## Image Record

(defrecord DockerImage [full-name
                        name
                        tag
                        user
                        labels
                        cmd
                        entrypoint]
  java.lang.Object
  (toString [_]
    (str full-name)))

(defn ->image
  ([^String full-name]
   (let [index (.lastIndexOf full-name ":")]
     (-> (if (pos? index)
           {:name      (subs full-name 0 index)
            :tag       (subs full-name (inc index))
            :full-name full-name}
           {:name      full-name
            :tag       "latest"
            :full-name (str full-name ":latest")})
         (merge
          {:labels     {}
           :user       "root"
           :env        []
           :cmd        []
           :entrypoint []})
         (map->DockerImage))))
  ([full-name inspect-result]
   (into
    (->image full-name)
    (when-let [{:keys [Config]} inspect-result]
      {:labels      (into {} (:Labels Config))
       :cmd         (into [] (:Cmd Config))
       :entrypoint  (into [] (:Entrypoint Config))}))))

;; ## Client Facade

(defprotocol+ DockerClient
  (pull-image [this image-name])
  (inspect-image [this image-name])
  (container
   ^into.docker.DockerContainer
   [this container-name image]))

(defn pull-image-record
  [client image-name]
  (some->> (or (inspect-image client image-name)
               (do (pull-image client image-name)
                   (inspect-image client image-name)))
           (->image image-name)))
