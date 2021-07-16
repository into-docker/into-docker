(ns into.build.add-cache-volume-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.add-cache-volume :as add-cache-volume]))

;; ## Helpers

(def ^:private volume-path
  "/tmp/cache")

(defn- cache-volume-of
  [{:keys [volumes]}]
  (first (filter #(= (:path %) volume-path) volumes)))

;; ## Tests

(defspec t-add-cache-volume (times 20)
  (prop/for-all
    [spec (gen/let [spec (s/gen ::spec/spec)]
            (assoc spec
                   :use-volumes? true
                   :use-cache-volume? true))]
    (let [{:keys [builder-image]} (add-cache-volume/run {:spec spec})
          builder-volume (cache-volume-of builder-image)]
      (:retain? builder-volume))))

(defspec t-add-cache-volume-does-nothing-if-disabled (times 20)
  (prop/for-all
    [spec (gen/let [spec (s/gen ::spec/spec)]
            (gen/elements
              [(assoc spec :use-volumes? false)
               (assoc spec :use-cache-volume? false)]))]
    (let [{:keys [builder-image]} (add-cache-volume/run {:spec spec})]
      (not (cache-volume-of builder-image)))))
