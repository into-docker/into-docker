(ns into.build.validate-spec-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.validate-spec :as validate-spec]
            [into.test.files :refer [with-temp-dir]]))

;; ## Generators

(defn- gen-valid-spec
  []
  (gen/one-of
    [(gen/fmap #(assoc % :target-image-name "test")
               (s/gen ::spec/spec))
     (gen/fmap #(assoc % :artifact-path "artifacts")
               (s/gen ::spec/spec))]))

(defn- gen-invalid-spec
  []
  (gen/fmap #(dissoc % :target-image-name :artifact-path)
            (s/gen ::spec/spec)))

;; ## Tests

(defspec t-validate-spec (times 10)
  (prop/for-all
    [spec (gen-valid-spec)]
    (with-temp-dir [tmp []]
      (let [spec (assoc spec :source-path tmp)
            data {:spec spec}
            data' (validate-spec/run data)]
        (= data data')))))

(defspec t-validate-spec-fails-without-target (times 10)
  (prop/for-all
    [spec (gen-invalid-spec)]
    (with-temp-dir [tmp []]
      (let [spec (assoc spec :source-path tmp)
            data {:spec spec}
            data' (validate-spec/run data)]
        (some? (:error data'))))))

(defspec t-validate-spec-fails-if-source-path-is-missing (times 10)
  (prop/for-all
    [spec (gen-valid-spec)]
    (with-temp-dir [tmp []]
      (let [spec (assoc spec :source-path (io/file tmp "missing"))
            data {:spec spec}
            data' (validate-spec/run data)]
        (some? (:error data'))))))
