(ns into.docker.mock-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.docker.mock :as mock]
            [into.docker :as docker])
  (:import [java.nio ByteBuffer]))

;; ## Helpers

(defn exec-script
  [exit-code]
  (fn [{:keys [container]} path]
    (mock/add-file container path path)
    (mock/->MockExec
      container
      [[:stderr "Working..."]
       [:stdout "OK."]]
      {:exit exit-code})))

(defn- as-byte-vec
  [^long value]
  (-> (ByteBuffer/allocate 4)
      (.putInt value)
      (.array)
      (vec)))

;; ## Tests

(defspec t-exec-stream-block (times 50)
  (prop/for-all
    [data   gen/bytes
     stream (gen/elements [:stdout :stderr])]
    (let [result (vec (mock/exec-stream-block stream data))]
      (and (= (case stream :stdout 1, :stderr 2) (first result))
           (= [0 0 0] (subvec result 1 4))
           (= (as-byte-vec (count data)) (subvec result 4 8))
           (= (vec data) (subvec result 8))))))

(defspec t-exec-container (times 50)
  (prop/for-all
    [script-path (s/gen ::spec/path)
     target-path (s/gen ::spec/path)]
    (let [container (-> (mock/container)
                        (mock/add-file script-path `(exec-script 0)))
          collector (mock/exec-collector)
          {:keys [exit]} (docker/exec-and-log
                           container
                           {:cmd [script-path target-path]}
                           collector)
          {:keys [stdout stderr]} @collector]
      (and (= 0 exit)
           (= "OK." stdout)
           (= "Working..." stderr)
           (mock/file-exists? container target-path)))))

#_(deftest t-transfer-between-containers
  (let [data (.getBytes "HELLO")
        from (-> (mock/container)
                 (mock/add-file "/dir/a" data)
                 (mock/add-file "/dir/b" data))
        to   (mock/container)]
    (docker/transfer-between-containers from to "/dir" "/target")
    (is (= #{"/target/dir/a" "/target/dir/b"}
           (mock/list-files to "/target/dir")))))

#_(deftest t-read-container-file
  (let [data "DATA"
        container (-> (mock/container)
                      (mock/add-file "/dir/file" data))]
    (is (= data (String. (docker/read-file container "/dir/file"))))))
