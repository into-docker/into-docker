(ns into.spec
  (:require [into.docker :as docker]
            [into.flow core]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; ## Generic

(s/def :into/image
  (-> (s/keys :req-un [:into/name
                       :into/tag
                       :into/full-name]
              :opt-un [:into/hash
                       :into/user])
      (s/with-gen
        (fn []
          (->> (gen/tuple (s/gen :into/name) (s/gen :into/tag))
               (gen/fmap
                (fn [[n t]]
                  {:name n
                   :tag  t
                   :full-name (str n ":" t)})))))))

(s/def :into/non-empty-string
  (s/and string? seq))

(s/def :into/name :into/non-empty-string)
(s/def :into/tag :into/non-empty-string)
(s/def :into/full-name :into/non-empty-string)
(s/def :into/hash :into/non-empty-string)
(s/def :into/path :into/non-empty-string)
(s/def :into/file #(instance? java.io.File %))
(s/def :into/error #(instance? Exception %))
(s/def :into/cleanup-error :into/error)
(s/def :into/container some?)
(s/def :into/interrupted? boolean?)
(s/def :into/paths (s/coll-of :into/path))
(s/def :into/user :into/non-empty-string)

;; ## Flow

(s/def :into/flow
  (s/keys :req-un [:into/spec
                   :into/well-known-paths
                   :into/client]
          :opt-un [:into/instances
                   :into/ci
                   :into/sources
                   :into/cleanup-error
                   :into/error
                   :into/interrupted?]))

;; ## Spec

(s/def :into/spec
  (s/keys :req-un [:into/source-path
                   :into/builder-image
                   :into/profile]
          :opt-un [:into/runner-image
                   :into/target-image
                   :into/target-path
                   :into/cache-spec
                   :into/ci-type]))

(s/def :into/builder-image :into/image)
(s/def :into/target-image :into/image)
(s/def :into/runner-image :into/image)
(s/def :into/source-path :into/path)
(s/def :into/target-path :into/path)
(s/def :into/profile :into/path)
(s/def :into/client #(satisfies? docker/DockerClient %))

(s/def :into/cache-spec
  (s/keys :req-un [:into/cache-from
                   :into/cache-to]))
(s/def :into/cache-from :into/path)
(s/def :into/cache-to :into/path)

;; ## VCS

(s/def :into/ci
  (s/keys :opt-un [:into/ci-type
                   :into/ci-revision
                   :into/ci-version
                   :into/ci-source]))
(s/def :into/ci-type #{"local" "github-actions"})
(s/def :into/ci-revision :into/non-empty-string)
(s/def :into/ci-version  :into/non-empty-string)
(s/def :into/ci-source   :into/non-empty-string)

;; ## Paths

(s/def :into/well-known-paths
  (s/keys :req-un [:into/source-directory
                   :into/artifact-directory
                   :into/working-directory
                   :into/build-script
                   :into/assemble-script
                   :into/ignore-file
                   :into/profile-directory]))

(s/def :into/source-directory   :into/path)
(s/def :into/artifact-directory :into/path)
(s/def :into/working-directory  :into/path)
(s/def :into/build-script       :into/path)
(s/def :into/profile-directory  :into/path)
(s/def :into/assemble-script    :into/path)
(s/def :into/ignore-file        :into/path)

;; ## Image Instance

(s/def :into/instances
  (s/keys :opt-un [:into/builder
                   :into/runner]))

(s/def :into/instance
  (s/keys :req-un [:into/image
                   :into/labels
                   :into/cmd]
          :opt-un [:into/container]))

(s/def :into/builder    :into/instance)
(s/def :into/runner     :into/instance)
(s/def :into/labels     (s/map-of  keyword?   string?))
(s/def :into/cmd        (s/nilable (s/coll-of string?)))
(s/def :into/entrypoint (s/nilable (s/coll-of string?)))

;; ## Sources

(s/def :into/sources
  (s/coll-of :into/source))

(s/def :into/source
  (s/keys :req-un [:into/file
                   :into/path]))

;; ## Cache

(s/def :into/cache
  (s/keys :req-un [:into/paths]))

;; ## Function Specs

(s/fdef into.flow.core/run-step
  :args (s/cat :next-fn fn? :data :into/flow)
  :ret  :into/flow)
