(ns into.build.read-build-profile-test
  (:require [into.build.read-build-profile :as step]
            [into.docker :as docker]
            [into spec]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.java.io :as io]
            [clojure.string :as string]))

;; ## Generators

(def ^:private gen-str
  (gen/such-that seq (gen/string-alphanumeric)))

(defn- gen-with-type
  [k gen]
  (gen/fmap #(hash-map :type k :value %) gen))

(def ^:private gen-var
  (->> (gen/tuple gen-str gen-str)
       (gen/fmap #(string/join "=" %))
       (gen-with-type :var)))

(def ^:private gen-other
  (->> (gen/one-of
         [(gen/fmap #(str "# " %) gen-str)
          (gen/return "")])
       (gen-with-type :other)))

(defn- gen-build-profile
  ([] (gen-build-profile [gen-var gen-other]))
  ([entries]
   (->> (gen/one-of entries)
        (gen/vector)
        (gen/fmap
          (fn [entries]
            {:value (string/join "\n" (map :value entries))
             :env   (keep
                      (fn [{:keys [type value]}]
                        (when (= type :var)
                          value))
                      entries)}))
        (gen/tuple gen-str)
        (gen/fmap
          (fn [[name m]]
            (assoc m :name name))))))

(def ^:private gen-base-flow
  (gen/hash-map
    :spec             (s/gen :into/spec)
    :well-known-paths (s/gen :into/well-known-paths)
    :instances        (s/gen :into/instances)))

;; ## Helpers

(defn- client-with-profiles
  [profile-directory profiles]
  (let [path->profile (->> (for [{:keys [name ^String value]} profiles]
                             [(str profile-directory "/" name)
                              (.getBytes value)])
                           (into {}))]
    (reify docker/DockerClient
      (read-container-file! [this container path]
        (path->profile path (byte-array 0))))))

(defn- select-profile
  [flow profiles {:keys [name] :as profile}]
  (-> flow
      (assoc-in [:spec :profile] name)
      (assoc :client (client-with-profiles
                       (get-in flow [:well-known-paths :profile-directory])
                       (concat profiles [profile])))))

(defn- read-builder-envs
  [flow]
  (get-in flow [:instances :builder :image :env]))

;; ## Tests

(defspec t-run-should-extract-correct-build-profile (times 20)
  (prop/for-all
    [profiles (gen/vector (gen-build-profile))
     profile  (gen/such-that (comp seq :env) (gen-build-profile))
     flow     gen-base-flow]
    (let [result (step/run (select-profile flow profiles profile))]
      (= (:env profile) (read-builder-envs result)))))

(defspec t-run-should-error-if-selected-profile-is-empty (times 20)
  (prop/for-all
    [profiles (gen/vector (gen-build-profile))
     profile  (->> (gen-build-profile [gen-other])
                   (gen/such-that #(not= (:name %) "default")))
     flow     gen-base-flow]
    (let [result (step/run (select-profile flow profiles profile))]
      (instance? IllegalStateException (:error result)))))

(defspec t-run-should-error-if-default-profile-is-empty (times 20)
  (prop/for-all
    [profiles (gen/vector (gen-build-profile))
     profile  (->> (gen-build-profile [gen-other])
                   (gen/fmap #(assoc % :name "default")))
     flow     gen-base-flow]
    (let [result (step/run (select-profile flow profiles profile))]
      (and (nil? (read-builder-envs result))
           (not (:error result))))))
