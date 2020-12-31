(ns into.utils.pattern-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.utils.pattern :as pattern]))

(defspec t-matcher-should-reject-by-default (times 20)
  (let [matcher (pattern/matcher [])]
    (prop/for-all [path (s/gen ::spec/path)]
      (not (matcher path)))))

(defspec t-matcher-should-accept-exact-matches (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [path])]
      (matcher path))))

(defspec t-matcher-should-accept-universal-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher ["**"])]
      (matcher path))))

(defspec t-matcher-should-accept-single-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [(str "?" (subs path 1))])]
      (matcher path))))

(defspec t-matcher-should-match-set-of-characters (times 20)
  (prop/for-all [first-char  gen/char-alpha
                 other-chars (gen/vector gen/char-alphanumeric)
                 reject?     gen/boolean
                 path        (s/gen ::spec/path)]
    (let [pattern (->> (cons first-char other-chars)
                       (string/join "")
                       (format "[%s%s]**" (if reject? "^" "")))
          matcher (pattern/matcher [pattern])
          path    (str first-char path)]
      (= (not reject?) (matcher path)))))

(defspec t-matcher-should-accept-range-of-characters (times 20)
  (prop/for-all [[range-start range-end]
                 (->> (gen/tuple gen/char-alpha gen/char-alpha)
                      (gen/such-that
                        (fn [[a b]]
                          (<= (int a) (int b)))))
                 reject?     gen/boolean
                 path        (s/gen ::spec/path)]
    (let [pattern (format "[%s%s-%s]**"
                          (if reject? "^" "")
                          range-start
                          range-end)
          matcher (pattern/matcher [pattern])
          path    (str range-start path)]
      (= (not reject?) (matcher path)))))

(defspec t-matcher-should-accept-by-path-prefix (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [path])]
      (matcher (str path "/" file)))))

(defspec t-matcher-should-accept-by-prefix-and-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/name)]
    (let [matcher (pattern/matcher [(str path "/*.clj")])]
      (matcher (str path "/" file ".clj")))))

(defspec t-matcher-should-reject-subdirectories (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 dir  (s/gen ::spec/path)
                 file (s/gen ::spec/name)]
    (let [matcher (pattern/matcher [(str path "/*.clj")])]
      (not (matcher (str path "/" dir "/" file ".clj"))))))

(defspec t-matcher-should-accept-subdirectories-with-wildcard (times 20)
  (prop/for-all [path (s/gen ::spec/path)
                 file (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [(str path "/**/*.clj")])]
      (matcher (str path "/" file ".clj")))))

(defspec t-matcher-should-explicitly-reject (times 20)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher ["**" (str "!" path)])]
      (not (matcher path)))))

(defspec t-matcher-should-accept-escaped-special-characters (times 20)
  (prop/for-all [special-char (gen/elements (vec "\\.[]{}()*+-?^$|"))
                 path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [(format "\\%s**" special-char)])
          path    (str special-char path)]
      (matcher path))))

(defspec t-matcher-should-ignore-comments (times 5)
  (prop/for-all [path (s/gen ::spec/path)]
    (let [matcher (pattern/matcher [(str "#" path)])]
      (not (matcher path)))))

(defspec t-matcher-should-raise-for-invalid-patterns (times 20)
  (prop/for-all [[invalid-pattern error-message]
                 (gen/elements
                   (seq {"[a"    "Incomplete character range."
                         "[a-[]" "Unexpected character"
                         "[z-a]" "Illegal character range"
                         "\\"    "Incomplete escape sequence."}))]
    (try
      (pattern/matcher [invalid-pattern])
      false
      (catch IllegalArgumentException e
        (let [message (.getMessage e)]
          (prn message)
          (string/starts-with?
            message
            (str "Invalid pattern: " error-message)))))))
