(ns into.utils.pattern-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.utils.pattern :as pattern]))

(defspec t-matcher-should-reject-by-default (times 20)
  (let [matcher (pattern/matcher [])]
    (prop/for-all [path (s/gen ::spec/path)]
      (not (matcher path)))))

(defspec t-matcher-should-accept-exact-matches (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [path])]
      (matcher path))))

(defspec t-matcher-should-accept-universal-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher ["**"])]
      (matcher path))))

(defspec t-matcher-should-accept-by-path-prefix (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [path])]
      (matcher (str path "/" file)))))

(defspec t-matcher-should-accept-by-prefix-and-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/name)]
    (let [matcher (pattern/matcher [(str path "/*.clj")])]
      (matcher (str path "/" file ".clj")))))

(defspec t-matcher-should-reject-subdirectories (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 dir  (s/gen ::spec/path)
                 file (s/gen ::spec/name)]
    (let [matcher (pattern/matcher [(str path "/*.clj")])]
      (not (matcher (str path "/" dir "/" file ".clj"))))))

(defspec t-matcher-should-accept-subdirectories-with-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [(str path "/**/*.clj")])]
      (matcher (str path "/" file ".clj")))))

(defspec t-matcher-should-explicitly-reject (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher ["**" (str "!" path)])]
      (not (matcher path)))))
