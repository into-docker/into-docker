(ns into.flow
  (:import [java.io InterruptedIOException]))

;; ## Validation Helper

(defn fail
  [data ^String error-message]
  (assoc data
         :error
         (IllegalStateException. error-message)))

(defn validate
  "Verify the given path in the given map against the
   predicate and set `:into/error` on mismatch."
  ([data path error-message]
   (validate data path some? error-message))
  ([data path pred error-message]
   (if (pred (get-in data path))
     data
     (fail data error-message))))

;; ## Flow Macro

(defn- handle-interrupt
  [data e]
  (if (Thread/interrupted)
    (assoc data :interrupted? true)
    (assoc data :error e)))

(defn ^:internal run-step
  [next-fn data]
  (try
    (if-not (or (:error data)
                (:interrupted? data))
      (next-fn data)
      data)
    (catch InterruptedException e
      (handle-interrupt data e))
    (catch InterruptedIOException e
      (handle-interrupt data e))
    (catch Exception e
      (assoc data :error e))))

(defmacro with-flow->
  "Run the given pipeline, and short-circuit the moment either `:into/error` or
   `:into/interrupted?` is set.

   Behaves like `clojure.core/->`."
  [form nxt & rst]
  (if (empty? rst)
    `(run-step #(-> % ~nxt) ~form)
    `(with-flow->
       (run-step #(-> % ~nxt) ~form)
       ~@rst)))
