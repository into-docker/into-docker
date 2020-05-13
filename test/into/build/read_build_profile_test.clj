(ns into.build.read-build-profile-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.constants :as constants]
            [into.docker.mock :as docker]
            [into.test.generators :refer [gen-file-with-comments]]
            [into.build.spec :as spec]
            [into.build.read-build-profile :as step]))

;; ## Helpers

(defn- make-container
  [build-profiles]
  (reduce
    (fn [container [filename {:keys [file]}]]
      (docker/add-file
        container
        (str (constants/path-for :profile-directory) "/" filename)
        file))
    (docker/container)
    build-profiles))

(defn- gen-profile
  []
  (->> (gen/tuple (s/gen ::spec/name) gen/string-ascii)
       (gen/fmap
         (fn [[env value]]
           (str env '= value)))
       (gen-file-with-comments {:min-lines 1})))

(defn- gen-profiles
  []
  (gen/map (s/gen ::spec/name) (gen-profile)))

(defn- gen-empty-profile
  []
  (gen/return {:lines [], :file ""}))

;; ## Tests

(defspec t-read-build-profile (times 20)
  (prop/for-all
    [profiles (gen-profiles)
     selected (gen-profile)
     spec     (s/gen ::spec/spec)]
    (let [profile-name (:profile spec)
          container (make-container (assoc profiles profile-name selected))]
      (= (:lines selected)
         (-> {:builder-container container
              :spec              spec}
             (step/run)
             (:builder-env))))))

(defspec t-read-build-profile-should-not-fail-if-empty-and-default (times 20)
  (prop/for-all
    [profiles (gen-profiles)
     selected (gen-empty-profile)
     spec     (gen/fmap #(assoc % :profile "default") (s/gen ::spec/spec))]
    (let [container (make-container (assoc profiles "default" selected))]
      (= []
         (-> {:builder-container container
              :spec              spec}
             (step/run)
             (:builder-env))))))

(defspec t-read-build-profile-should-fail-if-empty-and-not-default (times 20)
  (prop/for-all
    [profiles (gen-profiles)
     selected (gen-empty-profile)
     spec     (->> (s/gen ::spec/spec)
                   (gen/such-that (comp not #{"default"} :profile)))]
    (let [profile-name  (:profile spec)
          container     (make-container (assoc profiles profile-name selected))]
      (= (format "Build profile [%s] is empty." profile-name)
         (some-> {:builder-container container
                  :spec              spec}
                 (step/run)
                 ^Exception
                 (:error)
                 (.getMessage))))))
