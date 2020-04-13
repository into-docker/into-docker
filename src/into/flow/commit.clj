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
  [{:keys [spec]}]
  (try
    (let [path               (:source-path spec)
          {:keys [exit out]} (sh/sh "git" "rev-parse" "--short" "HEAD"
                                    :dir path)]
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

(defn- create-opencontainer-labels
  "See https://github.com/opencontainers/image-spec/blob/master/annotations.md"
  [data]
  (let [target (get-in data [:spec :target-image])]
    (->> {:created  (build-date)
          :revision (create-vcs-ref data)
          :title    (:name target)
          :version  (:tag target)}
         (map
           (fn [[k v]]
             [(str "org.opencontainers.image." (name k)) v]))
         (into {}))))

(defn- add-labels-and-env
  [data commit-spec]
  (merge
    {:labels (create-opencontainer-labels data)
     :env    []}
    commit-spec))

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
