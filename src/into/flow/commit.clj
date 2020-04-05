(ns into.flow.commit
  (:require [into.docker :as docker]
            [clojure.string :as string]
            [clojure.java.shell :as sh]
            [clojure.tools.logging :as log])
  (:import [java.time.format DateTimeFormatter]
           [java.time Instant ZoneId]))

(defn- add-target-suffix
  [^String target suffix]
  (let [index (.lastIndexOf target ":")]
    (if (neg? index)
      (str target ":latest" suffix)
      (str target suffix))))

(defn- create-vcs-ref
  []
  (let [{:keys [exit out]} (sh/sh "git" "rev-parse" "--short" "HEAD")]
    (if (= exit 0)
      (string/trim out)
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

(defn commit-container
  [{{:keys [target]} :spec
    client :client
    :as data} container-key suffix]
  (let [{:keys [container cmd]} (get data container-key)
        target-with-suffix (add-target-suffix target suffix)]
    (log/debugf "[into]   Committing image [%s] with CMD: %s"
                target-with-suffix
                cmd)
    (->> {:image target-with-suffix
          :cmd   cmd}
         (add-labels-and-env data)
         (docker/commit-container client container))
    data))
