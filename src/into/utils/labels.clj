(ns into.utils.labels
  (:require [into.utils.version :as v]
            [clojure.string :as string])
  (:import [java.time.format DateTimeFormatter]
           [java.time Instant ZoneId]))

;; ## Helpers

(let [fmt (-> (DateTimeFormatter/ofPattern
               "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
              (.withZone (ZoneId/of "UTC")))]
  (defn- rfc-3999-date
    []
    (.format fmt (Instant/now))))

;; ## Default Labels

(def ^:private default-labels
  {"org.into-docker.version"  (v/current-version)
   "org.into-docker.revision" (v/current-revision)
   "org.into-docker.url"      "https://github.com/into-docker/into-docker"})

;; ## Dynamic Labels

(defn- image-labels
  [{{:keys [builder-image runner-image]} :spec}]
  {"org.into-docker.builder-image" (:full-name builder-image)
   "org.into-docker.runner-image"  (:full-name runner-image)})

;; ## OCI Labels
;;
;; Standardized labels. We _could_ infer/compute the following ones:
;;
;; - created (current date)
;; - revision (git rev-parse --short HEAD)

(defn- oci-labels
  "See https://github.com/opencontainers/image-spec/blob/master/annotations.md"
  [data]
  (->> {:created  (rfc-3999-date)
        :revision (get-in data [:vcs :vcs-revision] "")}
       (keep
        (fn [[k v]]
          (when-not (string/blank? v)
            [(str "org.opencontainers.image." (name k)) v])))
       (into {})))

;; ## Clear Labels

(defn- clear-labels
  "Sets those labels that should be cleared. For example, the maintainer of the
   runner image is not the one of the created image."
  [data]
  {"maintainer" ""})

;; ## API

(defn create-labels
  "Create labels for the target image based on the given :into/spec."
  [data]
  (merge
   default-labels
   (image-labels data)
   (oci-labels data)
   (clear-labels data)))

(defn get-runner-image
  "Read the runner image from the given builder image instance"
  [instance]
  (get-in instance [:labels :org.into-docker.runner-image]))

(defn get-runner-cmd
  "Read the runner CMD from the given builder image instance"
  [instance]
  (get-in instance [:labels :org.into-docker.runner-cmd]))
