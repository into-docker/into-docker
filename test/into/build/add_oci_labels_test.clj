(ns into.build.add-oci-labels-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.add-oci-labels :as add-oci-labels]))

(defspec t-add-oci-labels (times 20)
  (prop/for-all
    [spec (s/gen ::spec/spec)
     image (s/gen ::spec/target-image)]
    (let [data (add-oci-labels/run
                 {:spec         spec
                  :target-image image})
          labels (get-in data [:target-image :labels])]
      (every? #(get labels %)
              ["org.opencontainers.image.version"
               "org.opencontainers.image.source"
               "org.opencontainers.image.created"]))))

(defspec t-add-oci-labels-does-nothing-without-target-image (times 20)
  (prop/for-all
    [spec (s/gen ::spec/spec)]
    (let [data {:spec spec}]
      (= data (add-oci-labels/run data)))))

(defspec t-add-oci-labels-for-github-actions (times 20)
  (prop/for-all
    [spec    (->> (s/gen ::spec/spec)
                  (gen/fmap #(assoc % :ci-type "github-actions")))
     image   (s/gen ::spec/target-image)
     gh-ref  gen/string-alphanumeric
     gh-sha  gen/string-alphanumeric
     gh-repo gen/string-alphanumeric]
    (let [envs {"GITHUB_REF"        gh-ref
                "GITHUB_SHA"        gh-sha
                "GITHUB_REPOSITORY" gh-repo}
          data (add-oci-labels/run
                 {:spec         spec
                  :target-image image}
                 {:getenv envs})
          labels (get-in data [:target-image :labels])]
      (and (= gh-sha (labels "org.opencontainers.image.revision"))
           (= gh-ref (labels "org.opencontainers.image.version"))
           (= (str "https://github.com/" gh-repo)
              (labels "org.opencontainers.image.source"))
           (seq (labels "org.opencontainers.image.created"))))))

(defspec t-add-oci-labels-for-github-actions-and-tag (times 20)
  (prop/for-all
    [spec    (->> (s/gen ::spec/spec)
                  (gen/fmap #(assoc % :ci-type "github-actions")))
     image   (s/gen ::spec/target-image)
     version gen/string-alphanumeric]
    (let [envs {"GITHUB_REF" (str "refs/tags/" version)}
          data (add-oci-labels/run
                 {:spec         spec
                  :target-image image}
                 {:getenv envs})
          labels (get-in data [:target-image :labels])]
      (= version (labels "org.opencontainers.image.version")))))
