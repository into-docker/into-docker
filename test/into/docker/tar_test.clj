(ns into.docker.tar-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]
             [generators :as gen]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.java.io :as io]
            [into.docker.tar :as tar])
  (:import [java.io File]))

;; ## Generators

(def gen-sources
  (->> (gen/tuple
        (->> (gen/such-that seq gen/string-alphanumeric)
             (gen/fmap #(.toLowerCase ^String %)))
        gen/bytes)
       (gen/fmap
        (fn [[path data]]
          {:source data
           :length (count data)
           :path path}))
       (gen/vector)
       (gen/fmap
        (fn [sources]
          (->> (group-by :path sources)
               (vals)
               (map first))))))

;; ## Helpers

(defn- sources-as-string
  [sources]
  (set
   (map
    (fn [source]
      (update source :source #(with-open [in (io/input-stream %)]
                                (slurp in))))
    sources)))

(defn- sources-from-path
  [path]
  (for [^File f (file-seq path)
        :when (.isFile f)]
    {:source f
     :length (.length f)
     :path   (.getName f)}))

(defn- with-temp-dir
  [f]
  (let [target (doto (File/createTempFile "into-docker-test-" "")
                 (.delete)
                 (.mkdir))]
    (try
      (f target)
      (finally
        (doseq [f (reverse (file-seq target))]
          (io/delete-file f))
        (.delete target)))))

;; ## Tests

(defspec t-tar-vs-untar (times 20)
  (prop/for-all
   [sources gen-sources]
    (let [extracted (with-open [in (io/input-stream (tar/tar sources))]
                      (tar/untar-seq in))]
      (= (sources-as-string sources)
         (sources-as-string extracted)))))

(defspec t-tar-gz-vs-untar (times 20)
  (prop/for-all
   [sources gen-sources]
    (let [extracted
          (tar/with-gz-open [in (io/input-stream (tar/tar-gz sources))]
            (tar/untar-seq in))]
      (= (sources-as-string sources)
         (sources-as-string extracted)))))

(defspec t-untar-to-path (times 10)
  (prop/for-all
   [sources gen-sources]
    (with-temp-dir
      (fn [target]
        (with-open [in (io/input-stream (tar/tar sources))]
          (tar/untar in target))
        (let [extracted (sources-from-path target)]
          (= (sources-as-string sources)
             (sources-as-string extracted)))))))

(defspec t-untar-to-path-with-file-fn (times 10)
  (prop/for-all
   [sources   gen-sources
    prefix-fn (gen/fmap #(fn [s] (str % s)) gen/string-alphanumeric)]
    (with-temp-dir
      (fn [target]
        (with-open [in (io/input-stream (tar/tar sources))]
          (tar/untar in target #(io/file %1 (prefix-fn %2))))
        (let [extracted (sources-from-path target)]
          (= (set
              (map
               #(update % :path prefix-fn)
               (sources-as-string sources)))
             (sources-as-string extracted)))))))
