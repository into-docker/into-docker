(ns into.docker.container
  (:require [into.docker :as docker]
            [clj-docker-client.core :as d]))

;; ## Invoke

(defn- throw-on-error
  [{:keys [message] :as result}]
  (if message
    (throw
     (IllegalStateException.
      (format "Operation failed: %s" message)))
    result))

(defn- invoke-put-container-archive
  [{:keys [containers]} container-id path stream]
  (->> {:op :PutContainerArchive
        :params {:id container-id
                 :path path
                 :inputStream stream}}
       (d/invoke containers)
       (throw-on-error)))

(defn- invoke-container-archive
  [{:keys [containers]} container-id path]
  (->> {:op :ContainerArchive
        :params {:id container-id
                 :path path}
        :as :stream}
       (d/invoke containers)))

(defn- invoke-exec
  [{:keys [containers]} container-id {:keys [cmd env root?]}]
  (->> {:op :ContainerExec
        :params {:id container-id
                 :execConfig {:AttachStderr true
                              :AttachStdout true
                              :User (when root? "root")
                              :Cmd (into [] cmd)
                              :Env (into [] env)}}}
       (d/invoke containers)
       (throw-on-error)
       (:Id)))

(defn- invoke-exec-start
  [{:keys [exec]} id]
  (->> {:op :ExecStart
        :params {:id id
                 :execStartConfig {:Detach false}}
        :as :stream}
       (d/invoke exec)))

(defn- invoke-exec-inspect
  [{:keys [exec]} id]
  (->> {:op :ExecInspect
        :params {:id id}}
       (d/invoke exec)
       (throw-on-error)))

(defn- invoke-commit-container
  [{:keys [commit]}
   container-id
   {:keys [name tag cmd entrypoint env labels]}]
  (->> {:op :ImageCommit
        :params {:container container-id
                 :repo name
                 :tag  tag
                 :containerConfig {:Cmd cmd
                                   :Entrypoint entrypoint
                                   :Env (into [] env)
                                   :Labels (into {} labels)}}}
       (d/invoke commit)
       (throw-on-error)))

(defn- invoke-run-container
  [{:keys [containers]} container-name {:keys [full-name user volumes]}]
  (let [{:keys [Id]}
        (->> {:op :ContainerCreate
              :params {:name  container-name
                       :body {:Image full-name
                              :User  user
                              :Cmd   ["tail" "-f" "/dev/null"]
                              :HostConfig
                              {:Mounts (vec
                                         (for [[path volume] volumes]
                                           {:Target   path,
                                            :Source   volume,
                                            :Type     "volume",
                                            :ReadOnly false}))}}}}
             (d/invoke containers)
             (throw-on-error))
        _ (->> {:op :ContainerStart
                :params {:id Id}}
               (d/invoke containers)
               (throw-on-error))]
    Id))

(defn- invoke-stop-container
  [{:keys [containers]} container-id]
  (throw-on-error
   (d/invoke
    containers
    {:op :ContainerDelete
     :params {:id    container-id
              :force true}})))

;; ## Exec

(deftype DockerExec [clients container data id stream]
  docker/DockerExec
  (exec-container [this]
    container)
  (exec-stream [this]
    stream)
  (exec-result [this]
    (let [{:keys [ExitCode]} (invoke-exec-inspect clients id)]
      (assoc data :exit ExitCode))))

;; ## Container

(deftype DockerContainer [clients container-name image container-id]
  docker/DockerContainer
  (container-name [this]
    container-name)
  (run-container [this]
    (let [id (invoke-run-container clients container-name image)]
      (reset! container-id id)))
  (commit-container [this target-image]
    (invoke-commit-container clients @container-id target-image))
  (cleanup-container [this]
    (invoke-stop-container clients @container-id))
  (stream-from-container [this path]
    (invoke-container-archive clients @container-id path))
  (stream-into-container [this path tar-stream]
    (invoke-put-container-archive clients @container-id path tar-stream))
  (run-container-command [this data]
    (let [id     (invoke-exec clients @container-id data)
          stream (invoke-exec-start clients id)]
      (->DockerExec clients this data id stream)))

  java.lang.Object
  (toString [this]
    (str (:name image))))

(defn make
  [clients container-name image]
  (->DockerContainer clients container-name image (atom nil)))
