(ns into.build.ci
  (:require [into.flow
             [core :as flow]]
            [into.utils
             [log :as log]
             [version :as v]]
            [clojure.string :as string]))

;; ## Handling of different CI systems

(def ^:private github-spec
  {:type           "github-actions"
   :base-url       "https://github.com"
   :ref-env        "GITHUB_REF"
   :revision-env   "GITHUB_SHA"
   :repository-env "GITHUB_REPOSITORY"})

(def ^:private ci-specs
  [github-spec])

;; ## Attach Information

(defn- find-ci-spec
  [{:keys [ci-type]}]
  (some
   #(when (= ci-type (:type %))
      %)
   ci-specs))

(defn- add-ci-env
  [getenv data k env f]
  (if-let [v (getenv env)]
    (assoc-in data [:ci k] (f v))
    data))

(defn- extract-ci-version
  [s]
  (if (string/starts-with? s "refs/tags/")
    (subs s 10)
    s))

(defn- expand-ci-source
  [base-url s]
  (string/join "/" [base-url s]))

(defn- attach-ci-information
  [{:keys [spec] :as data}
   {:keys [getenv]
    :or {getenv #(System/getenv %)}}]
  (if-let [{:keys [type base-url ref-env revision-env repository-env]}
           (find-ci-spec spec)]
    (let [add (partial add-ci-env getenv)]
      (-> data
          (assoc-in [:ci :ci-type] type)
          (add :ci-revision revision-env   identity)
          (add :ci-version  ref-env        extract-ci-version)
          (add :ci-source   repository-env #(expand-ci-source base-url %))))
    (assoc-in data [:ci :ci-type] "local")))

(defn- fallback-to-local-ci-revision
  [{:keys [spec] :as data}
   {:keys [get-revision]
    :or {get-revision v/read-revision}}]
  (or (when-not (get-in data [:ci :ci-revision])
        (when-let [v (get-revision spec)]
          (assoc-in data [:ci :ci-revision] v)))
      data))

(defn- log-ci-information
  [data]
  (when-let [{:keys [ci-type ci-revision ci-version ci-source]} (:ci data)]
    (-> data
        (log/debug "CI: %s" ci-type)
        (cond->
         ci-revision (log/debug "  Revision: %s" ci-revision)
         ci-version  (log/debug "  Version:  %s" ci-version)
         ci-source   (log/debug "  Source:   %s" ci-source))))
  data)

;; ## Flow

(defn run
  "Attach VCS information for the given source directory."
  [{:keys [spec] :as data} & [opts]]
  (flow/with-flow-> data
    (attach-ci-information opts)
    (fallback-to-local-ci-revision opts)
    (log-ci-information)))
