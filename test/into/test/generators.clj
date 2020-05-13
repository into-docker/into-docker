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
  ([line-gen]
   (gen-file-with-comments {} line-gen))
  ([{:keys [min-lines] :or {min-lines 0}} line-gen]
   (gen/let [lines (-> (gen/tuple
                         (gen/vector
                           (gen/hash-map :line line-gen)
                           min-lines
                           10)
                         (gen/vector
                           (gen/one-of
                             [(gen/hash-map :other (gen-comment))
                              (gen/hash-map :other (gen-newline))])))
                       (gen/bind
                         (fn [[lines other]]
                           (gen/shuffle (into lines other)))))]
     {:lines (->> (keep :line lines)
                  (map string/trim))
      :file  (->> (concat
                    (keep :line lines)
                    (keep :other lines))
                  (string/join "\n"))})))

(defn gen-unique-paths
  "Generate a set of unique paths that do not cause directory vs. file conflicts
   when they are all treated as file paths, e.g. `0/0` vs. `0/0/0` where the
   second `0` would need to be both a file and directory."
  [num-elements]
  (let [segment-gen (gen/not-empty gen/string-alphanumeric)]
    (-> segment-gen
        (gen/set {:num-elements num-elements})
        (gen/bind
          (fn [filenames]
            (gen/let [segments (gen/vector
                                 (gen/such-that
                                   (complement filenames)
                                   (gen/vector segment-gen 0 5))
                                 (count filenames))]
              (map #(string/join "/" (cons %1 %2)) filenames segments)))))))
