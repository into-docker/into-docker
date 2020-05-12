(ns into.flow-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.test :refer [deftest is]]
            [into.flow :as flow])
  (:import [java.io InterruptedIOException]))

;; ## Helper

(defn- inc-counter
  [data]
  (update data :counter (fnil inc 0)))

;; ## Tests

(defspec t-run-step-calls-next-fn (times 20)
  (prop/for-all
    [state (gen/hash-map :counter gen/pos-int)]
    (= (inc-counter state)
       (flow/run-step inc-counter state))))

(defspec t-run-step-does-nothing-when-interrupted (times 20)
  (prop/for-all
    [state (gen/hash-map
             :counter      gen/pos-int
             :interrupted? (gen/return true))]
    (= state (flow/run-step inc-counter state))))

(defspec t-run-step-does-nothing-when-error (times 20)
  (prop/for-all
    [state (gen/hash-map
             :counter      gen/pos-int
             :error        (gen/fmap #(Exception. ^String %) gen/string-ascii))]
    (= state (flow/run-step inc-counter state))))

(defspec t-run-step-does-nothing-when-failed (times 20)
  (prop/for-all
    [state (gen/hash-map :counter gen/pos-int)
     error-message gen/string-ascii]
    (let [{:keys [counter ^Exception error]}
          (->> (flow/fail state error-message)
               (flow/run-step inc-counter))]
      (and (= error-message (.getMessage error))
           (= (:counter state) counter)))))

(defspec t-run-step-does-nothing-when-validation-failed (times 20)
  (prop/for-all
    [state        (gen/hash-map :counter gen/pos-int)
     error-message gen/string-ascii]
    (let [{:keys [counter ^Exception error]}
          (->> (flow/validate state [:counter] neg? error-message)
               (flow/run-step inc-counter))]
      (and (= error-message (.getMessage error))
           (= (:counter state) counter)))))

(defspec t-run-step-handles-interrupt (times 20)
  (prop/for-all
    [state  (gen/hash-map :counter gen/pos-int)
     ex     (gen/elements [(InterruptedException.) (InterruptedIOException.)])]
    (let [fut (future
                (flow/run-step
                  (fn [_]
                    (.interrupt (Thread/currentThread))
                    (throw ex))
                  state))
          {:keys [counter error interrupted? interrupt-error]} @fut]
      (and (nil? error)
           (true? interrupted?)
           (= ex interrupt-error)
           (= (:counter state) counter)))))

(defspec t-run-step-handles-exceptions (times 20)
  (prop/for-all
    [state        (gen/hash-map :counter gen/pos-int)
     error-message gen/string-ascii]
    (let [{:keys [counter ^Exception error]}
          (flow/run-step
            (fn [_]
              (throw
                (Exception. ^String error-message)))
            state)]
      (and (= error-message (.getMessage error))
           (= (:counter state) counter)))))

(deftest t-run-flow->
  (let [{:keys [counter error]} (flow/with-flow-> {:counter 0}
                                  (inc-counter)
                                  (inc-counter)
                                  (flow/validate [:counter] "Counter not set.")
                                  (flow/fail "It failed.")
                                  (inc-counter))]
    (is (= 2 counter))
    (is (= "It failed." (.getMessage ^Exception error)))))
