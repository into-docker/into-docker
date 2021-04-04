(ns into.test.generators
  (:require [into.build.spec :as spec]
            [into.constants :as constants]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [clojure.string :as string]))

;; ## Utils

(defn maybe
  [g]
  (gen/one-of [g (gen/return nil)]))

;; ## Paths

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
  ([] (gen/sized gen-unique-paths))
  ([num-elements]
   (let [segment-gen (gen/not-empty (gen/fmap string/lower-case gen/string-alphanumeric))]
     (-> segment-gen
         (gen/set {:num-elements num-elements})
         (gen/bind
           (fn [filenames]
             (gen/let [segments (gen/vector
                                  (gen/such-that
                                    (complement filenames)
                                    (gen/vector segment-gen 0 5))
                                  (count filenames))]
               (map #(string/join "/" (cons %1 %2)) filenames segments))))))))

;; ## Images

;; ### Spec + Builder + Runner

(defn gen-spec-and-images
  []
  (->> (gen/let [spec    (s/gen ::spec/spec)
                 builder (s/gen ::spec/builder-image)
                 runner  (s/gen ::spec/runner-image)
                 user    (maybe (s/gen ::spec/user))]
         {:spec    (assoc spec :builder-image-name (:full-name builder))
          :builder (-> builder
                       (assoc-in [:labels constants/runner-image-label]
                                 (:full-name runner))
                       (cond->
                         user (assoc-in [:labels constants/builder-user-label]
                                        user)))
          :runner  (when (:target-image-name spec)
                     runner)
          :user    (or user "root")})
       (gen/such-that
         #(not= (-> % :builder :full-name)
                (-> % :runner :full-name)))))

;; ### Label

(defn with-label
  [image-gen label]
  (gen/let [image image-gen
            v     gen/string-alphanumeric]
    (assoc-in image [:labels label] v)))

(defn without-label
  [image-gen label]
  (gen/fmap #(update % :labels dissoc label) image-gen))
