(ns into.utils.pattern-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]
             [generators :as gen]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.string :as string]
            [into.utils.pattern :as pattern]))

(defn gen-file
  ([]
   (gen/bind gen/string-alphanumeric gen-file))
  ([extension]
   (->> gen/string-alphanumeric
        (gen/such-that seq)
        (gen/fmap #(str % "." extension)))))

(defn gen-path
  ([]
   (gen/bind (gen/such-that seq gen/string-alphanumeric) gen-path))
  ([extension]
   (->> gen/string-alphanumeric
        (gen/such-that seq)
        (gen/vector)
        (gen/fmap #(string/join "/" %))
        (gen/fmap #(str % "." extension)))))

(defspec t-matcher-should-reject-by-default (times 20)
  (let [matcher (pattern/matcher [])]
    (prop/for-all [path (gen-path)]
      (not (matcher path)))))

(defspec t-matcher-should-accept-exact-matches (times 20)
  (prop/for-all [path (gen-path)]
    (let [matcher (pattern/matcher [path])]
      (matcher path))))

(defspec t-matcher-should-accept-with-wildcard (times 20)
  (prop/for-all [[pattern path] (gen/let [extension (gen/such-that seq gen/string-alphanumeric)
                                          path      (gen-file extension)
                                          prefix    (gen/elements ["" "/" "//"])]
                                  [(str prefix "*." extension) path])]
    (let [matcher (pattern/matcher [pattern])]
      (matcher path))))

(defspec t-matcher-should-explicitly-reject (times 20)
  (prop/for-all [path (gen-path)]
    (let [matcher (pattern/matcher ["**" (str "!" path)])]
      (not (matcher path)))))
