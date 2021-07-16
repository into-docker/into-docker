(ns into.utils.sha1
  (:import [java.security MessageDigest]))

(defn sha1
  "Create SHA-1 hash from string."
  [^String s]
  (let [instance (MessageDigest/getInstance "SHA1")
        ^bytes data (.getBytes s "UTF-8")]
    (->> (.digest instance data)
         (map #(format "%02x" %))
         (apply str))))
