(ns into.docker.client
  "Docker client implementation based on clj-docker-client"
  (:require [into.docker :as proto]
            [into.docker
             [streams :as streams]]
            [peripheral.core :refer [defcomponent]]
            [clj-docker-client.core :as docker])
  (:import [java.util UUID]
           [java.io InputStream]))

;; ## Helpers

(defn- throw-on-error
  [{:keys [message] :as result}]
  (if message
    (throw
     (IllegalStateException.
      (format "Operation failed: %s" message)))
    result))

(defn- stream-into
  [{:keys [containers]} {:keys [id]} stream path]
  (->> {:op :PutContainerArchive
        :params {:id id
                 :path path
                 :inputStream stream}}
       (docker/invoke containers)
       (throw-on-error)))

(defn- stream-from
  [{:keys [containers]} {:keys [id]} path]
  ;; Unfortunately, we seem to have to load the full data into memory since
  ;; the stream returned by `ContainerArchive` does not set the stream length
  ;; that is used in `PutContainerArchive` to set the `Content-Length`
  ;; header.
  (with-open [^InputStream source (->> {:op :ContainerArchive
                                        :params {:id id
                                                 :path path}
                                        :as :stream}
                                       (docker/invoke containers))]
    (streams/cached-stream source)))

(defn- invoke-exec
  [{:keys [containers exec]} {:keys [id]} command env]
  (let [{:keys [Id]} (->> {:op :ContainerExec
                           :params {:id id
                                    :execConfig {:AttachStderr true
                                                 :AttachStdout true
                                                 :Cmd command
                                                 :Env env}}}
                          (docker/invoke containers)
                          (throw-on-error))]
    (->> {:op :ExecStart
          :params {:id Id
                   :execStartConfig {:Detach false}}
          :as :stream}
         (docker/invoke exec))))

(defn- invoke-commit-container
  [{:keys [commit]} {:keys [id]} {:keys [^String image cmd env labels]}]
  (let [[repo tag] (.split image ":" 2)]
    (->> {:op :ImageCommit
          :params {:container id
                   :repo repo
                   :tag  (or tag "latest")
                   :containerConfig {:Cmd cmd
                                     :Env (into [] env)
                                     :Labels (into {} labels)}}}
         (docker/invoke commit)
         (throw-on-error))))

(defn- invoke-run-container
  [{:keys [containers]} container-name image]
  (let [{:keys [Id] :as created}
        (->> {:op :ContainerCreate
              :params {:name  container-name
                       :body {:Image image
                              :Cmd   ["tail" "-f" "/dev/null"]
                              :Tty   true
                              :Init  true}}}
             (docker/invoke containers)
             (throw-on-error))
        result (->> {:op :ContainerStart
                     :params {:id Id}}
                    (docker/invoke containers)
                    (throw-on-error))]
    {:name container-name
     :id   Id}))

(defn- invoke-stop-container
  [{:keys [containers]} {:keys [id]}]
  (throw-on-error
   (docker/invoke
    containers
    {:op :ContainerStop
     :params {:id id}}))
  (throw-on-error
   (docker/invoke
    containers
    {:op :ContainerDelete
     :params {:id id}})))

(defn- invoke-pull-image
  [{:keys [images]} image]
  (->> {:op :ImageCreate
        :params {:fromImage image}}
       (docker/invoke images)
       (throw-on-error))
  {:image image})

(defn- invoke-inspect-image
  [{:keys [images]} image]
  (let [result (->> {:op :ImageInspect
                     :params {:name image}}
                    (docker/invoke images))]
    (when-not (:message result)
      result)))

;; ## Component

(defcomponent DockerClient [uri]
  :assert/uri (seq uri)
  :conn       (docker/connect {:uri uri})
  :images     (docker/client {:conn conn, :category :images})
  :containers (docker/client {:conn conn, :category :containers})
  :commit     (docker/client {:conn conn, :category :commit})
  :exec       (docker/client {:conn conn, :category :exec})

  proto/DockerClient
  (pull-image [this image]
    (invoke-pull-image this image))

  (inspect-image [this image]
    (invoke-inspect-image this image))

  (run-container [this container-name image]
    (invoke-run-container this container-name image))

  (commit-container
    [this container data]
    (invoke-commit-container this container data))

  (cleanup-container [this container]
    (invoke-stop-container this container))

  (read-container-file! [this container path]
    (with-open [^InputStream stream (invoke-exec this container ["cat" path] [])]
      (streams/exec-bytes stream :stdout)))

  (copy-into-container! [this stream container path]
    (stream-into this container stream path))

  (copy-between-containers! [this
                             source-container
                             target-container
                             from-path to-path]
    (with-open [^InputStream in (stream-from this source-container from-path)]
      (stream-into this target-container in to-path)))

  (execute-command!  [this container command env log-fn]
    (with-open [^InputStream stream (invoke-exec this container command env)]
      (doseq [e (streams/log-seq stream)]
        (log-fn e)))))

;; ## Constructor

(defn make
  ([]
   (make {}))
  ([components]
   (map->DockerClient components)))
