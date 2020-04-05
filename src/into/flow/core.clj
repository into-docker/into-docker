(ns into.flow.core
  (:import [java.io InterruptedIOException]))

;; ## Validation Helper

(defn validate
  "Verify the given path in the given map against the
   predicate and set `:into/error` on mismatch."
  ([data path error-message]
   (validate data path some? error-message))
  ([data path pred ^String error-message]
   (if (pred (get-in data path))
     data
     (assoc data
            :into/error
            (IllegalStateException. error-message)))))

;; ## Flow Macro

(defn- handle-interrupt
  [data e]
  (if (Thread/interrupted)
    (assoc data :into/interrupted? true)
    (assoc data :into/error e)))

(defn ^:internal run-step
  [next-fn data]
  (try
    (if-not (or (:into/error data)
                (:into/interrupted? data))
      (next-fn data)
      data)
    (catch InterruptedException e
      (handle-interrupt data e))
    (catch InterruptedIOException e
      (handle-interrupt data e))
    (catch Exception e
      (assoc data :into/error e))))

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
