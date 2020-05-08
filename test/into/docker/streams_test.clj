(ns into.docker.streams-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]
             [generators :as gen]]
            [clojure.test :refer [deftest is testing]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.string :as string]
            [into.docker.streams :as streams])
  (:import [java.io PipedInputStream PipedOutputStream]
           [java.nio ByteBuffer ByteOrder]))

;; ## Test Data

(defn- ->block
  "Create a block as it would be returned from a docker exec stream,
   prefixed as either stdout or stderr."
  [stream ^String data-string]
  (let [^bytes data (.getBytes data-string)
        len         (count data)]
    (-> (ByteBuffer/allocate (+ len 8))
        (.put (byte (case stream :stdout 0x01 :stderr 0x02)))
        (.position 4)
        (.order ByteOrder/BIG_ENDIAN)
        (.putInt len)
        (.put data)
        (.array))))

(def gen-block
  (gen/let [data-string (->> (gen/tuple
                              gen/string-alphanumeric
                              (gen/elements ["" "\n"]))
                             (gen/fmap #(apply str %)))
            stream (gen/elements [:stdout :stderr])]
    {:string data-string
     :bytes  (.getBytes ^String data-string)
     :stream stream
     :block  (->block stream data-string)}))

;; ## Helper

(defn- block-stream
  ^PipedInputStream [blocks]
  (let [out (PipedOutputStream.)]
    (doto (Thread.
           #(with-open [out out]
              (doseq [{:keys [block]} blocks]
                (.write out block 0 (count block)))))
      (.start))
    (PipedInputStream. out)))

(defn- collect-data-string
  [stream sq]
  (->> sq
       (filter (comp #{stream} :stream))
       (map :bytes)
       (map #(String. ^bytes %))
       (string/join)))

(defn- collect-log-string
  [stream sq]
  (->> sq
       (filter (comp #{stream} :stream))
       (map :line)
       (string/join)))

;; ## Tests

(deftest t-block-generator
  (testing "test utility function '->block'"
    (let [data  "HELLO"
          len   (count data)
          bytes (.getBytes data)]
      (is (= (concat [1 0 0 0 0 0 0 len] bytes)
             (seq (->block :stdout data))))
      (is (= (concat [2 0 0 0 0 0 0 len] bytes)
             (seq (->block :stderr data)))))))

(defspec t-exec-seq (times 20)
  (prop/for-all
   [blocks (gen/vector gen-block)
    stream (gen/elements [:stdout :stderr])]
    (with-open [in (block-stream blocks)]
      (let [sq (streams/exec-seq in)]
        (= (collect-data-string stream blocks)
           (collect-data-string stream sq))))))

(defspec t-exec-bytes (times 20)
  (prop/for-all
   [blocks (gen/vector gen-block)
    stream (gen/elements [:stdout :stderr])]
    (with-open [in (block-stream blocks)]
      (let [data (streams/exec-bytes in stream)]
        (= (collect-data-string stream blocks)
           (String. data))))))

(defspec t-log-seq (times 20)
  (prop/for-all
   [blocks (gen/vector gen-block)
    stream (gen/elements [:stdout :stderr])]
    (with-open [in (block-stream blocks)]
      (let [sq (streams/log-seq in)]
        (= (collect-data-string stream blocks)
           (collect-log-string stream sq))))))
