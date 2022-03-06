(ns into.docker.client
  "Docker client implementation based on clj-docker-client"
  (:require [into.docker :as proto]
            [into.docker.container :as container]
            [into.docker.progress :as progress]
            [clj-docker-client.core :as docker]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [jsonista.core :as json]))

;; ## Helpers

(defn- from-json
  [s]
  (json/read-value s json/keyword-keys-object-mapper))

(defn- report-pull-progress
  [^java.io.InputStream in]
  (let [printer (progress/progress-printer)]
    (with-open [rdr (io/reader in)]
      (doseq [data (map from-json (line-seq rdr))]
        (printer data)))))

(defn- throw-on-error
  [{:keys [message] :as result}]
  (if message
    (throw
     (IllegalStateException.
      (format "Operation failed: %s" message)))
    result))

(defn- invoke-pull-image
  [{{:keys [images]} :clients, platform :platform} image]
  (let [{:keys [name tag]} (proto/->image image)]
    (try
      (->> {:op :ImageCreate
            :params {:fromImage name
                     :tag       tag
                     :platform  platform}
            :throw-exception? true
            :throw-entire-message? true
            :as :stream}
           (docker/invoke images)
           (report-pull-progress))
      (catch Exception e
        (throw-on-error
          (-> e ex-data :body from-json)))))
  {:image image})

(letfn [(matches-platform? [platform os architecture]
          (or (not platform)
              (string/starts-with? platform (str os "/" architecture))))]
  (defn- invoke-inspect-image
    [{{:keys [images]} :clients, platform :platform} image]
    (let [{:keys [full-name]} (proto/->image image)
          {:keys [RepoTags Os Architecture message] :as result}
          (->> {:op :ImageInspect
                :params {:name full-name}}
               (docker/invoke images))]
      (when-not message
        (when (contains? (set RepoTags) full-name)
          (when (matches-platform? platform Os Architecture)
            result))))))

;; ## Component

(defrecord DockerClient [platform uri conn clients]
  proto/DockerClient
  (pull-image [this image]
    (invoke-pull-image this image))
  (inspect-image [this image]
    (invoke-inspect-image this image))
  (container [_ container-name image]
    (container/make clients container-name image)))

;; ## Constructor

(defn make
  ([]
   (make {}))
  ([components]
   (map->DockerClient components)))

(defn- get-docker-default-platform
  []
  (System/getenv "DOCKER_DEFAULT_PLATFORM"))

(defn- get-docker-uri
  []
  (or (System/getenv "DOCKER_HOST")
      "unix:///var/run/docker.sock"))

(defn- get-docker-api-version
  []
  (some->> (System/getenv "DOCKER_API_VERSION")
           (str "v")))

(defn make-from-env
  []
  (make
    {:platform    (get-docker-default-platform)
     :uri         (get-docker-uri)
     :api-version (get-docker-api-version)}))

;; ## Start

(defn start
  [{:keys [uri api-version] :as client}]
  {:pre [(seq uri)]}
  (let [conn    (docker/connect {:uri uri})
        mkcli   #(docker/client
                   {:conn        conn
                    :category    %
                    :api-version api-version})
        clients {:images     (mkcli :images)
                 :containers (mkcli :containers)
                 :commit     (mkcli :commit)
                 :volumes    (mkcli :volumes)
                 :exec       (mkcli :exec)}]
    (assoc client :conn conn, :clients clients)))
