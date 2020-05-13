(ns into.main-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log logged?]]
            [into.main :as main]))

;; ## Helper

(defmacro with-main-mock
  [& body]
  `(with-redefs [main/exit identity
                 main/register-sigint-handler! (constantly nil)]
     (with-log
       ~@body)))

;; ## Tests

(deftest t-main-should-print-version
  (with-main-mock
    (main/-main "--version")
    (is (logged? 'into.main
                 :info
                 #"into \d+\.\d+\.\d+(-[A-Z0-9-]+)? \(revision [a-f0-9]+\)"))))
