(ns into.utils.signal
  (:import [sun.misc Signal SignalHandler]))

(defn register-sigint-handler!
  "Register a handler for SIGINT that will interrupt the current thread instead
   of exiting without a chance for cleanup.

   NOTE: A shutdown hook does not seem to work with GraalVM compilation which is
   the reason we implemented it like this."
  []
  (let [^Thread t (Thread/currentThread)
        handled? (ref false)]
    (Signal/handle
     (Signal. "INT")
     (reify SignalHandler
       (handle [_this _signal]
         (dosync
          (when-not @handled?
            (ref-set handled? true)
            (.interrupt t))))))))
