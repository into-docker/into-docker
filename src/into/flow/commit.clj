(ns into.flow.commit
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.docker :as docker]
            [clojure.string :as string]
            [clojure.java.shell :as sh])
  (:import [java.time.format DateTimeFormatter]
           [java.time Instant ZoneId]))

;; ## Labels

(defn- create-vcs-ref
  []
  (try
    (let [{:keys [exit out]} (sh/sh "git" "rev-parse" "--short" "HEAD")]
      (if (= exit 0)
        (string/trim out)
        ""))
    (catch Exception _
      "")))

(let [fmt (-> (DateTimeFormatter/ofPattern
               "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
              (.withZone (ZoneId/of "UTC")))]
  (defn- build-date
    []
    (.format fmt (Instant/now))))

(defn- add-labels-and-env
  [_ {:keys [image cmd] :as commit-spec}]
  (let [[_ _ name version] (re-find #"^(.+/)?([^/]+):([^/:]+)$" image)
        vcs-ref (create-vcs-ref)]
    (merge
     {:labels {"org.label-schema.schema-version" "1.0"
               "org.label-schema.vcs-ref"        vcs-ref
               "org.label-schema.vcs-url"        ""
               "org.label-schema.build-date"     (build-date)
               "org.label-schema.name"           name
               "org.label-schema.version"        version}
      :env    []}
     commit-spec)))

;; ## Commit

(defn- commit-container!
  [{{:keys [target-image]} :spec
    client :client
    :as data} container-key suffix]
  (let [{:keys [container cmd]} (get-in data [:instances container-key])
        {:keys [full-name]} target-image]
    (log/debug data
               "Committing image [%s] with CMD: %s"
               full-name
               cmd)
    (->> {:image full-name
          :cmd   cmd}
         (add-labels-and-env data)
         (docker/commit-container client container))
    data))

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/emph "Saving image [%s] ..."
              (get-in data [:spec :target-image :full-name]))
    (commit-container! :runner "")))
