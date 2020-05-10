(ns into.utils.task-test
  (:require [into.utils.task :as task]
            [clojure.tools.logging.test :refer [logged? with-log]]
            [clojure.test :refer [deftest testing is]]))

;; ## Fixtures

(def ^:private task-spec
  {:usage "call-it"
   :cli [["-o" "--option VAL" "an option"
          :id :opt]]
   :needs [:opt]
   :run (juxt :arguments :options)})

;; ## Helpers

(defn- has-log?
  [msg]
  (logged? 'into.utils.task :info msg))

(defn- has-error?
  [msg]
  (logged? 'into.utils.task :error msg))

(defn- has-help?
  []
  (and (has-log? "Usage: call-it")
       (has-log? "")
       (has-log? #"  -o, --option VAL")
       (has-log? #"  -v, --verbose")
       (has-log? #"  -h, --help")))

;; ## Tests

(deftest t-make
  (let [task (task/make task-spec)]
    (testing "shows help by default"
      (with-log
        (is (= :help (task [])))
        (is (has-help?))))
    (testing "shows help if desired"
      (doseq [opt ["-h" "--help"]]
        (with-log
          (is (= :help (task [opt])))
          (is (has-help?)))))
    (testing "errors on missing options"
      (with-log
        (is (= :missing (task ["arg"])))
        (is (has-error? #"Missing option: -o/--option"))))
    (testing "calls run function"
      (with-log
        (is (= [["arg"] {:opt "X", :verbosity 0}]
               (task ["-o" "X" "arg"])))))))

(deftest t-make-without-args
  (let [task (task/make (assoc task-spec :no-args? true))]
    (testing "errors on missing options and no arguments"
      (with-log
        (is (= :missing (task [])))
        (is (has-error? #"Missing option: -o/--option"))))
    (testing "shows help if desired"
      (doseq [opt ["-h" "--help"]]
        (with-log
          (is (= :help (task [opt])))
          (is (has-help?)))))
    (testing "calls run function"
      (with-log
        (is (= [[] {:opt "X", :verbosity 0}]
               (task ["-o" "X"])))))))
