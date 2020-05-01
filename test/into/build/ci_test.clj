(ns into.build.ci-test
  (:require [into.build.ci :as ci]
            [into spec]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; ## Generators

(defn- gen-spec-for-ci
  [ci-type]
  (->> (s/gen :into/spec)
       (gen/fmap #(assoc % :ci-type ci-type))))

(def ^:private gen-ref
  (->> (gen/such-that seq (gen/string-alphanumeric))
       (gen/fmap #(str "refs/tags/" %))))

(def ^:private gen-sha
  (->> (gen/vector
        (gen/elements [0 1 2 3 4 5 6 7 8 9 'a 'b 'c 'd 'e 'f])
        40)
       (gen/fmap #(apply str %))))

(def ^:private gen-repository
  (->> (gen/tuple
        (gen/such-that seq (gen/string-alphanumeric))
        (gen/such-that seq (gen/string-alphanumeric)))
       (gen/fmap #(str (% 0) "/" (% 1)))))

;; ## Tests

(defspec t-run-should-attach-github-actions-information (times 20)
  (prop/for-all [spec (gen-spec-for-ci "github-actions")
                 getenv (gen/hash-map
                         "GITHUB_REF"        gen-ref
                         "GITHUB_SHA"        gen-sha
                         "GITHUB_REPOSITORY" gen-repository)]
    (let [{:keys [ci]} (ci/run {:spec spec} {:getenv getenv})]
      (and (= (:ci-type ci) "github-actions")
           (= (:ci-revision ci) (getenv "GITHUB_SHA"))
           (seq (:ci-version ci))
           (.endsWith ^String (:ci-source ci) (getenv "GITHUB_REPOSITORY"))))))

(defspec t-run-should-fallback-to-local-revision (times 20)
  (prop/for-all [spec (gen/bind (gen/string-alphanumeric) gen-spec-for-ci)
                 revision       gen-sha]
    (let [{:keys [ci]} (ci/run {:spec spec}
                               {:getenv       {}
                                :get-revision (constantly revision)})]
      (and (= (:ci-type ci) "local")
           (= (:ci-revision ci) revision)))))
