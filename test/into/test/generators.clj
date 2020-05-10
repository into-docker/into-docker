(ns into.test.generators
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as string]))

(defn gen-file-with-comments
  "Generate a file consisting of lines provided by `line-gen`, interspersed
   with comments and blank lines."
  [line-gen]
  (gen/let [lines    (gen/vector line-gen)
            comments (->> gen/string-alphanumeric
                          (gen/fmap #(str "# " %))
                          (gen/vector))
            newlines (->> (gen/elements ["\n" "\r" "  \n"])
                          (gen/vector))]
    {:lines lines
     :file  (->> (concat lines comments newlines)
                 (shuffle)
                 (string/join "\n"))}))
