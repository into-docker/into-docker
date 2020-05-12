(ns into.log-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]
             [generators :as gen]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.tools.logging.test :refer [logged? the-log with-log]]
            [into.log :as log])
  (:import [java.util.regex Pattern]))

;; ## Helpers

(defn- literal
  [fmt & args]
  (re-pattern (Pattern/quote (apply format fmt args))))

;; ## Tests

(defspec t-report-errors (times 20)
  (prop/for-all
    [ex-message  (gen/one-of [gen/string-ascii (gen/return nil)])
     cex-message (gen/one-of [gen/string-ascii (gen/return nil)])]
    (with-log
      (let [data (cond-> {}
                   ex-message
                   (assoc :error (Exception. ^String ex-message))
                   cex-message
                   (assoc :cleanup-error (Exception. ^String cex-message)))]
        (log/report-errors data)
        (and (or (not ex-message)
                 (and (logged? 'into.log :error
                               (literal "[into] %s" ex-message))
                      (logged? 'into.log :debug Exception
                               (literal "[into] [Exception] Stacktrace"))))
             (or (not cex-message)
                 (and (logged? 'into.log :error
                               (literal "[into] An error occured during cleanup: %s" cex-message))
                      (logged? 'into.log :debug Exception
                               (literal "[into] [Exception] Stacktrace"))))
             (or ex-message cex-message (empty? (the-log))))))))

(defspec t-report-exec (times 20)
  (prop/for-all
    [entry (gen/hash-map
             :stream (gen/elements [:stdout :stderr])
             :line   gen/string-ascii)]
    (with-log
      (log/report-exec entry)
      (logged? 'into.log :info
               (re-pattern (format "[into] |   .+\\Q%s\\E.+" (:line entry)))))))

(defspec t-as-file-size (times 50)
  (let [input-gen #(gen/tuple
                     (gen/return %1)
                     (gen/choose
                       (min Long/MAX_VALUE (Math/pow 1024 %2))
                       (min Long/MAX_VALUE (Math/pow 1024 (inc %2)))))]
    (prop/for-all
      [[suffix value] (gen/one-of
                        [(input-gen "B" 1)
                         (input-gen "KiB" 1)
                         (input-gen "MiB" 2)
                         (input-gen "GiB" 3)
                         (input-gen "TiB" 4)
                         (input-gen "PiB" 5)
                         (input-gen "EiB" 6) ])]
      (let [size (log/as-file-size value)]
        (and (re-matches #"\d+.\d+(.i)?B" size)
             (.endsWith size suffix))))))
