(ns into.utils.labels
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [java.time.format DateTimeFormatter]
           [java.time Instant ZoneId]))

;; ## Helpers

(let [fmt (-> (DateTimeFormatter/ofPattern
               "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
              (.withZone (ZoneId/of "UTC")))]
  (defn- rfc-3999-date
    []
    (.format fmt (Instant/now))))

(defn- read-revision
  [{:keys [source-path] :or {source-path "."}}]
  (try
    (let [{:keys [exit out]} (sh "git" "rev-parse" "--short" "HEAD"
                                 :dir source-path)]
      (if (= exit 0)
        (string/trim out)
        ""))
    (catch Exception _
      (log/tracef "Failed to read revision from '%s'." source-path)
      "")))

;; ## Default Labels

(defmacro ^:private current-version
  "If built with Leiningen, this will resolve to a literal string containing
   the project version."
  []
  (System/getProperty "into.version"))

(defmacro ^:private current-revision
  "This will resolve to the current commit as a literal string that will
   persist after AOT compilation."
  []
  (read-revision {}))

(def ^:private default-labels
  {"org.into-docker.version"  (current-version)
   "org.into-docker.revision" (current-revision)
   "org.into-docker.url"      "https://github.com/into-docker/into-docker"})

;; ## Dynamic Labels

(defn- image-labels
  [{:keys [builder-image runner-image]}]
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
  [spec]
  (->> {:created (rfc-3999-date)
        :revision (read-revision spec)}
       (keep
        (fn [[k v]]
          (when-not (string/blank? v)
            [(str "org.opencontainers.image." (name k)) v])))
       (into {})))

;; ## Clear Labels

(defn- clear-labels
  "Sets those labels that should be cleared. For example, the maintainer of the
   runner image is not the one of the created image."
  [spec]
  {"maintainer" ""})

;; ## API

(defn create-labels
  "Create labels for the target image based on the given :into/spec."
  [spec]
  (merge
   default-labels
   (image-labels spec)
   (oci-labels spec)
   (clear-labels spec)))

(defn get-runner-image
  "Read the runner image from the given builder image instance"
  [instance]
  (get-in instance [:labels :org.into-docker.runner-image]))

(defn get-runner-cmd
  "Read the runner CMD from the given builder image instance"
  [instance]
  (get-in instance [:labels :org.into-docker.runner-cmd]))
