(ns into.build.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string]))

;; ## Generic

(s/def ::non-empty-string
  (-> (s/and string? seq)
      (s/with-gen
        #(gen/such-that seq (gen/string-alphanumeric)))))

(s/def ::name ::non-empty-string)

(s/def ::image-name
  (s/or :full-name ::full-name
        :name      ::name))

(s/def ::full-name
  (-> (s/and ::non-empty-string #(re-matches #".+:.+" %))
      (s/with-gen
        (fn []
          (->> (gen/tuple (s/gen ::name) (s/gen ::tag))
               (gen/fmap #(string/join ":" %)))))))

(s/def ::path
  (s/with-gen ::non-empty-string
    (fn []
      (->> (gen/vector
            (->> (s/gen ::non-empty-string)
                 (gen/fmap string/lower-case))
            1 10)
           (gen/fmap #(string/join "/" %))))))

(s/def ::file-path
  (s/with-gen ::non-empty-string
    (fn []
      (->> (gen/tuple
            (s/gen ::path)
            (gen/return "/")
            (s/gen ::non-empty-string)
            (gen/return ".")
            (s/gen ::non-empty-string))
           (gen/fmap string/join)))))

(s/def ::pattern string?)

(s/def ::paths (s/coll-of ::path))
(s/def ::file-paths (s/coll-of ::file-path))
(s/def ::patterns (s/coll-of ::pattern))

;; ## Spec

(s/def ::spec
  (s/keys :req-un [::source-path
                   ::builder-image-name
                   ::profile
                   ::use-cache-volume?
                   ::use-volumes?]
          :opt-un [::artifact-path
                   ::cache-from
                   ::cache-to
                   ::ci-type
                   ::target-image-name]))

(s/def ::source-path ::path)
(s/def ::artifact-path ::path)
(s/def ::builder-image-name ::image-name)
(s/def ::target-image-name ::image-name)
(s/def ::profile ::name)
(s/def ::ci-type #{"github-actions", "local"})
(s/def ::use-volumes? boolean?)
(s/def ::use-cache-volume? boolean?)

;; ## Image

(s/def ::image
  (s/keys :req-un [::name
                   ::tag
                   ::full-name
                   ::user
                   ::labels
                   ::cmd
                   ::entrypoint]))

(s/def ::image-with-volumes
  (s/merge ::image (s/keys :opt-un [::volumes])))

(s/def ::tag        ::non-empty-string)
(s/def ::user       ::non-empty-string)
(s/def ::labels     (s/map-of (s/or :k keyword? :s string?) string?))
(s/def ::cmd        (s/coll-of string?))
(s/def ::entrypoint (s/coll-of string?))
(s/def ::volumes    (s/coll-of ::volume))
(s/def ::volume
  (s/keys :req-un [::name
                   ::path
                   ::retain?]))
(s/def ::retain? boolean?)

(s/def ::builder-image ::image-with-volumes)
(s/def ::runner-image  ::image)
(s/def ::target-image  ::image)

;; ## Env

(s/def ::env (s/coll-of ::env-var))
(s/def ::env-var
  (-> (s/and string? #(re-matches #".+=.*" %))
      (s/with-gen
        (fn []
          (->> (gen/tuple (gen/string-alphanumeric) (gen/string-alphanumeric))
               (gen/fmap
                (fn [k v]
                  (str k "=" v))))))))

(s/def ::builder-env ::env)
(s/def ::runner-env ::env)

;; ## Paths

(s/def ::cache-from ::path)
(s/def ::cache-to ::path)
(s/def ::cache-paths ::paths)
(s/def ::source-paths ::paths)
(s/def ::ignore-paths ::patterns)
