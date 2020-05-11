(ns into.test.generators
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as string]))

(defn gen-comment
  []
  (->> gen/string-alphanumeric
       (gen/fmap #(str "# " %))))

(defn gen-newline
  []
  (gen/elements ["\n" "\r" "  \n"]))

(defn gen-file-with-comments
  "Generate a file consisting of lines provided by `line-gen`, interspersed
   with comments and blank lines. The result will be a hash map with keys
   `:lines` (actual content lines in order of appearance) and `:file` (full
   file including comments/newlines)."
  [line-gen]
  (gen/let [lines (gen/vector
                    (gen/one-of
                      [(gen/hash-map :line line-gen)
                       (gen/hash-map :other (gen-comment))
                       (gen/hash-map :other (gen-newline))]))]
    {:lines (keep :line lines)
     :file  (->> (concat
                   (keep :line lines)
                   (keep :other lines))
                 (string/join "\n"))}))
