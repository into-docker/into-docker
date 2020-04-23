(ns into.docker.streams
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io
            InputStream
            ByteArrayInputStream
            ByteArrayOutputStream]
           [java.nio ByteBuffer ByteOrder]))

;; ## Log Stream Handling

(defn- read-length
  [^InputStream stream]
  (dotimes [_ 3] (.read stream))
  (let [lb (byte-array 4)]
    (.read stream lb)
    (.. (ByteBuffer/wrap lb)
        (order ByteOrder/BIG_ENDIAN)
        (getInt))))

(defn- read-next-block
  "There seems to be an 8 byte prefix in Docker command output, e.g.:

   ```
   01 00 00 00 00 00 00 4c
   --|--------|----------|
   s | unused |  length  |
   ```

   The first byte indicates the stream (01 = stdout, 02 = stderr).
   I have no idea where this is documented and I don't know if skipping
   it will break our code down the line."
  [^InputStream stream]
  (when-let [stream-key (case (.read stream)
                          1 :stdout
                          2 :stderr
                          -1 nil)]
    (let [buffer (byte-array 512)]
      (with-open [out (ByteArrayOutputStream.)]
        (loop [len (read-length stream)]
          (when (> len 0)
            (let [read-len (.read stream buffer 0 (min 512 len))]
              (when (pos? read-len)
                (.write out buffer 0 read-len))
              (recur (- len read-len)))))
        {:bytes (.toByteArray out)
         :stream stream-key}))))

(defn exec-seq
  [^InputStream stream]
  (->> (repeatedly #(read-next-block stream))
       (take-while some?)))

(defn- block->lines
  [{:keys [^bytes bytes stream]}]
  (->> (string/split (String. bytes "UTF-8") #"(?<=\n)")
       (map #(hash-map :stream stream :line %))))

(defn- merge-lines
  [a {:keys [line]}]
  (update a :line str line))

(defn- has-linebreak?
  [{:keys [^String line]}]
  (.endsWith line "\n"))

(defn- same-stream?
  [a b]
  (= (:stream a) (:stream b)))

(defn- group-lines
  "Lines that get transmitted in multiple pieces need to be combined as to
   not have awkward linebreaks in logs."
  [sq]
  (when-let [[line & rst] (seq sq)]
    (lazy-seq
     (or (if-not (has-linebreak? line)
           (if-let [[other & rst] rst]
             (if (same-stream? line other)
               (group-lines
                (cons (merge-lines line other) rst)))))
         (cons line (group-lines rst))))))

(defn log-seq
  [^InputStream stream]
  (->> (for [block (exec-seq stream)
             line (block->lines block)]
         line)
       (group-lines)))

(defn exec-bytes
  ^bytes [stream stream-key]
  (with-open [out (ByteArrayOutputStream.)]
    (doseq [{:keys [^"[B" bytes stream]} (exec-seq stream)
            :when (= stream stream-key)]
      (.write out bytes))
    (.toByteArray out)))

;; ## Reading

(defn cached-stream
  [^InputStream stream]
  (with-open [out (ByteArrayOutputStream.)]
    (io/copy stream out)
    (ByteArrayInputStream. (.toByteArray out))))
