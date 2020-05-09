(ns into.build.add-oci-labels
  (:require [into.flow :as flow]
            [into.log :as log]
            [into.utils.version :as v]
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

;; ## Handling of different CI systems

(def ^:private github-spec
  {:type           "github-actions"
   :base-url       "https://github.com"
   :ref-env        "GITHUB_REF"
   :revision-env   "GITHUB_SHA"
   :repository-env "GITHUB_REPOSITORY"})

(def ^:private ci-specs
  [github-spec])

;; ## Read Information

(defn- find-ci-spec
  [{{:keys [ci-type]} :spec}]
  (some
   #(when (= ci-type (:type %))
      %)
   ci-specs))

(defn- add-ci-env
  [getenv data k env f]
  (if-let [v (getenv env)]
    (assoc data k (f v))
    data))

(defn- extract-ci-version
  [s]
  (if (string/starts-with? s "refs/tags/")
    (subs s 10)
    s))

(defn- expand-ci-source
  [base-url s]
  (string/join "/" [base-url s]))

(defn- create-ci-information
  [data {:keys [getenv] :or {getenv #(System/getenv %)}}]
  (if-let [{:keys [type
                   base-url
                   ref-env
                   revision-env
                   repository-env]}
           (find-ci-spec data)]
    (let [add (partial add-ci-env getenv)]
      (-> {:type type}
          (add :revision revision-env   identity)
          (add :version  ref-env        extract-ci-version)
          (add :source   repository-env #(expand-ci-source base-url %))))
    {:type "local"}))

(defn- fallback-to-local-ci-revision
  [ci data {:keys [get-revision] :or {get-revision v/read-revision}}]
  (merge
   {:revision (get-revision (:spec data))
    :version  ""
    :source   ""}
   ci))

;; ## Log

(defn- log-ci-information
  [data {:keys [type revision version source]}]
  (log/debug "CI: %s" type)
  (when-not (string/blank? revision)
    (log/debug "  Revision: %s" revision))
  (when-not (string/blank? version)
    (log/debug "  Version: %s" version))
  (when-not (string/blank? source)
    (log/debug "  Source:  %s" source))
  data)

;; ## Add labels to target image

(defn- attach-ci-information
  [data ci]
  (->> (-> ci
           (dissoc :type)
           (assoc :created (rfc-3999-date)))
       (map
        (fn [[k v]]
          [(str "org.opencontainers.image." (name k)) v]))
       (into {})
       (update-in data [:target-image :labels] merge)))

;; ## Flow

(defn run
  "Attach VCS information for the given source directory."
  [data & [opts]]
  (if (:target-image data)
    (if-let [ci (some-> (create-ci-information data opts)
                        (fallback-to-local-ci-revision data opts))]
      (flow/with-flow-> data
        (log-ci-information ci)
        (attach-ci-information ci))
      data)
    data))
