(ns into.build.prepare-target-image-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.constants :as constants]
            [into.test.generators :refer [with-label without-label]]
            [into.build.spec :as spec]
            [into.build.prepare-target-image :as step]))

;; ## Generators

(defn gen-spec
  []
  (->> (gen/tuple
         (s/gen ::spec/target-image-name)
         (s/gen ::spec/spec))
       (gen/fmap
         (fn [[n s]]
           (assoc s :target-image-name n)))))

;; ## Tests

(defspec t-prepare-target-image-should-transfer-cmd-and-entrypoint (times 20)
  (prop/for-all
    [runner-image (s/gen ::spec/runner-image)
     spec         (gen-spec)]
    (let [{:keys [error target-image]}
          (->> {:spec         spec
                :runner-image runner-image}
               (step/run))]
      (and (not error)
           (= (:cmd runner-image) (:cmd target-image))
           (= (:entrypoint runner-image) (:entrypoint target-image))))))

(defspec t-prepare-target-image-should-override-cmd (times 20)
  (prop/for-all
    [runner-image  (s/gen ::spec/runner-image)
     builder-image (-> (s/gen ::spec/builder-image)
                       (with-label constants/runner-cmd-label)
                       (without-label constants/runner-entrypoint-label))
     spec          (gen-spec)]
    (let [expected (get-in builder-image [:labels constants/runner-cmd-label])
          {:keys [error target-image]}
          (->> {:spec          spec
                :builder-image builder-image
                :runner-image  runner-image}
               (step/run))]
      (and (not error)
           (= ["sh" "-c" expected] (:cmd target-image))))))

(defspec t-prepare-target-image-should-override-entrypoint (times 20)
  (prop/for-all
    [runner-image  (s/gen ::spec/runner-image)
     builder-image (-> (s/gen ::spec/builder-image)
                       (with-label constants/runner-entrypoint-label)
                       (without-label constants/runner-cmd-label))
     spec          (gen-spec)]
    (let [expected (get-in builder-image [:labels constants/runner-entrypoint-label])
          {:keys [error target-image]}
          (->> {:spec          spec
                :builder-image builder-image
                :runner-image  runner-image}
               (step/run))]
      (and (not error)
           (= [] (:cmd target-image))
           (= ["sh" "-c" (str expected " $@") "--"]
              (:entrypoint target-image))))))

(defspec t-prepare-target-image-should-do-nothing-if-not-desired (times 10)
  (prop/for-all
    [spec (->> (s/gen ::spec/spec)
               (gen/fmap #(dissoc % :target-image-name)))]
    (let [result (step/run {:spec spec})]
      (and (not (:error result))
           (not (contains? result :target-image))))))
