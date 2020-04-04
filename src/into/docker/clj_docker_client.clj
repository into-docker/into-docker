(ns into.docker.clj-docker-client
  "Docker client implementation based on clj-docker-client"
  (:require [into.docker :as proto]
            [peripheral.core :refer [defcomponent]]
            [clj-docker-client.core :as docker]
            [jsonista.core :as json]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.util UUID]
           [java.io BufferedReader]))

;; ## Component

(defn- consume-stream
  [log-fn ^java.io.InputStream stream]
  (with-open [stream stream
              r      (io/reader stream)]
    (doseq [line (line-seq r)
            :let [first-byte (if-not (empty? line)
                               (byte (.charAt line 0)))]]
      (cond-> line
        (= first-byte 1) (subs 8)
        :always log-fn))))

(defcomponent DockerClient [uri]
  :assert/uri (seq uri)
  :conn       (docker/connect {:uri uri})
  :images     (docker/client {:conn conn, :category :images})
  :containers (docker/client {:conn conn, :category :containers})
  :commit     (docker/client {:conn conn, :category :commit})
  :exec       (docker/client {:conn conn, :category :exec})

  proto/DockerClient
  (pull-image
    [this image]
    (->> {:op :ImageCreate
          :params {:fromImage image}}
         (docker/invoke images))
    {:image image})

  (inspect-image
    [this image]
    (let [result (->> {:op :ImageInspect
                       :params {:name image}}
                      (docker/invoke images))]
      (when-not (:message result)
        result)))

  (run-container
    [this container-name image]
    (let [{:keys [Id] :as created}
          (->> {:op :ContainerCreate
                :params {:name  container-name
                         :body {:Image image
                                :Cmd   ["tail" "-f" "/dev/null"]
                                :Tty   true
                                :Init  true}}}
               (docker/invoke containers))
          result (->> {:op :ContainerStart
                       :params {:id Id}}
                      (docker/invoke containers))]
      {:name container-name
       :id   Id}))

  (commit-container
    [this {:keys [id] :as container} image cmd]
    (->> {:op :ImageCommit
          :params {:container id
                   :repo image
                   :tag  "latest"
                   :containerConfig {:Cmd cmd}}}
         (docker/invoke commit)))

  (cleanup-container
    [this {:keys [id]}]
    (doto containers
      (docker/invoke
        {:op :ContainerStop
         :params {:id id}})
      (docker/invoke
        {:op :ContainerDelete
         :params {:id id}})))

  (copy-into-container!
    [this tar-stream container path]
    (->> {:op :PutContainerArchive
          :params {:id (:id container)
                   :path path
                   :inputStream tar-stream}}
         (docker/invoke containers)))

  (copy-between-containers!
    [this source-container target-container from-path to-path]
    ;; Unfortunately, we seem to have to load the full data into memory since
    ;; copying from one stream to the other seems to send a zero
    ;; 'Content-Length' header.
    (let [byte-data  (with-open [source (->> {:op :ContainerArchive
                                              :params {:id (:id source-container)
                                                       :path from-path}
                                              :as :stream}
                                             (docker/invoke containers))
                                 buffer (java.io.ByteArrayOutputStream.)]
                       (io/copy source buffer)
                       (.toByteArray buffer))]
      (with-open [in (io/input-stream byte-data)]
        (proto/copy-into-container! this in target-container to-path))
      {:from-path from-path
       :to-path   to-path
       :tar-size  (count byte-data)}))

  (execute-command!
    [this {:keys [id]} command env log-fn]
    (let [{:keys [Id]} (->> {:op :ContainerExec
                             :params {:id id
                                      :execConfig {:AttachStdin false
                                                   :AttachStdout true
                                                   :AttachStderr true
                                                   :Tty false
                                                   :Env env
                                                   :Cmd command}}}
                            (docker/invoke containers))
          _ (->> {:op :ExecStart
                  :params {:id Id
                           :execStartConfig {:Detach false
                                             :Tty    false}}
                  :as :stream}
                 (docker/invoke exec)
                 (consume-stream log-fn))
          {:keys [ExitCode ProcessConfig]} (->> {:op :ExecInspect
                                                 :params {:id Id}}
                                                (docker/invoke exec))]
      {:exit-code ExitCode, :cmd ProcessConfig})))

;; ## Constructor

(defn make
  ([]
   (make {}))
  ([components]
   (map->DockerClient components)))
