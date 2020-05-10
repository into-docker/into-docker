(ns into.test.snapshot
  (:require [clojure.test :refer [is]]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import [java.io PushbackReader]))

(defn snapshot*
  [path value]
  (let [file (io/file path)]
    (if (.isFile file)
      (with-open [in (PushbackReader. (io/reader file))]
        (edn/read in))
      (do
        (io/make-parents file)
        (spit file (pr-str value))
        value))))

(defn create-snapshot-path
  [file snapshot-name]
  (let [f (io/file file)]
    (io/file "test"
             (.getParentFile f)
             "snapshots"
             (str (name snapshot-name) ".snap.edn"))))

(defmacro is-snap?
  "Compare value with a snapshot of the given name, calling `clojure.test/is`
   under the hood."
  [snapshot-name value]
  `(let [~'value ~value
         ~'path  (create-snapshot-path ~*file* ~snapshot-name)
         ~'snap  (snapshot* ~'path ~'value)]
     (is ~'(= snap value))))
