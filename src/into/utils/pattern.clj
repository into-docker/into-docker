(ns into.utils.pattern
  (:require [clojure.string :as string])
  (:import [java.nio.file Paths]))

;; ## .dockerignore matching
;;
;; Matching is done using Go’s filepath.Match rules. A preprocessing step
;; removes leading and trailing whitespace and eliminates . and .. elements
;; using Go’s filepath.Clean. Lines that are blank after preprocessing are
;; ignored.
;;
;; Beyond Go’s filepath.Match rules, Docker also supports a special wildcard
;; string ** that matches any number of directories (including zero). For
;; example, **/*.go will exclude all files that end with .go that are found
;; in all directories, including the root of the build context.
;;
;; Lines starting with ! (exclamation mark) can be used to make exceptions to
;; exclusions.

;; ### Golang Pattern Grammar
;;
;;   pattern:
;;          { term }
;;   term:
;;       '*'         matches any sequence of non-Separator characters
;;       '?'         matches any single non-Separator character
;;       '[' [ '^' ] { character-range } ']'
;;                  character class (must be non-empty)
;;       c           matches character c (c != '*', '?', '\\', '[')
;;       '\\' c      matches character c
;;
;;   character-range:
;;       c           matches character c (c != '\\', '-', ']')
;;       '\\' c      matches character c
;;       lo '-' hi   matches character c for lo <= c <= hi
;;

;; ### Helpers

(let [needs-quote? (set "\\.[]{}()*+-?^$|")]
  (defn- quote-char
    [c]
    (if (needs-quote? c)
      (str \\ c)
      (str c))))

(defn- raise
  [^String message]
  (throw
   (IllegalArgumentException. message)))

(defn- raise-with-pattern
  [pattern' pattern ^Exception e]
  (let [error-index (- (count pattern') (count pattern))]
    (raise
     (format
      (str "Invalid pattern: %s%n"
           "  Index:    %d%n"
           "  Error at: %s%n"
           "            "
           (string/join (repeat error-index " ")) "^")
      (.getMessage e)
      error-index
      pattern'
      (apply str pattern)))))

;; ### Wildcards

(let [one-char     "[^/]"
      multi-char   (str one-char \*)
      multi-dir    (str "(" one-char "+/)*")
      multi-end    (str "(" one-char "+/)*" one-char "*")]
  (defn- compile-wildcard-single
    [pattern]
    {:regex one-char
     :rst (next pattern)})

  (defn- compile-wildcard
    [[a b c :as pattern]]
    (cond (not= a \*) (raise "Cannot call function for non-wildcard.")
          (not= b \*) {:regex multi-char, :rst (next pattern)}
          (nil? c)    {:regex multi-end,  :rst nil}
          (= c \/)    {:regex multi-dir,  :rst (drop 3 pattern)}
          :else       {:regex multi-dir,  :rst (drop 2 pattern)})))

;; ### Literals

(defn- compile-literal
  [[c & rst]]
  {:regex (quote-char c)
   :rst   rst})

(defn- compile-escaped-literal
  [pattern]
  (if-let [rst (next pattern)]
    (compile-literal rst)
    (raise "Incomplete escape sequence.")))

;; ### Range: `[abc]` or `[a-zA-Z]` or a mixture

(defn- append-range-pattern
  [^StringBuilder sb pattern]
  (let [append-quoted #(.append sb (quote-char %))
        append-char   #(.append sb %)]
    (loop [_         sb
           [c & rst] (seq pattern)
           dash-allowed? false]
      (case c
        nil (raise "Incomplete character range.")
        \[ (raise "Unexpected character, '[' cannot be used in range.")
        \] rst
        \\ (recur (append-quoted (first rst)) (next rst) true)
        \- (recur
            (if dash-allowed?
              (append-char c)
              (append-quoted c))
            rst
            (not dash-allowed?))
        (recur (append-quoted c) rst true)))))

(defn- compile-range
  [[_ c :as pattern]]
  (let [sb (doto (StringBuilder.)
             (.append \[))
        rst (if (= c \^)
              (do
                (.append sb c)
                (append-range-pattern sb (drop 2 pattern)))
              (append-range-pattern sb (next pattern)))]
    (.append sb \])
    {:regex (.toString sb)
     :rst   rst}))

;; ### Compilation

(defn- append-prefix
  [^StringBuilder sb]
  (.append sb "^"))

(defn- append-suffix
  [^StringBuilder sb]
  (.append sb "(/.*)?"))

(defn- append-pattern
  [^StringBuilder sb pattern']
  (loop [pattern (seq pattern')]
    (when pattern
      (let [{:keys [^String regex rst]}
            (try
              (case (first pattern)
                \\ (compile-escaped-literal pattern)
                \? (compile-wildcard-single pattern)
                \* (compile-wildcard pattern)
                \[ (compile-range pattern)
                (compile-literal pattern))
              (catch Exception e
                (raise-with-pattern pattern' pattern e)))]
        (.append sb regex)
        (recur rst)))))

(defn- normalize-pattern
  [pattern]
  (-> pattern
      (Paths/get (into-array String []))
      (.normalize)
      (str)
      (cond-> (string/starts-with? pattern "/") (subs 1))))

(defn- compile-pattern
  [{:keys [pattern] :as data}]
  (let [pattern-str (->> (doto (StringBuilder.)
                           (append-prefix)
                           (append-pattern
                             (normalize-pattern pattern))
                           (append-suffix))
                         (.toString))]
    (try
      (assoc data :pattern (re-pattern pattern-str))
      (catch IllegalArgumentException e
        (raise-with-pattern pattern pattern e)))))

(defn- as-pattern
  [pattern]
  (let [pattern (string/trim pattern)]
    (when-not (string/starts-with? pattern "#")
      (-> (if (string/starts-with? pattern "!")
            {:selector :exclude
             :pattern  (subs pattern 1)}
            {:selector :include
             :pattern   pattern})
          (compile-pattern)))))

(defn matcher
  "Create a function that checks all `.dockerignore` patterns against a given
   string, return the last matching pattern's result. This function will return
   true if there is at least one match and no explicit exclude."
  [patterns]
  (let [patterns (reverse (keep as-pattern patterns))]
    (fn [name]
      (-> (some
           (fn [{:keys [selector pattern]}]
             (when (re-matches pattern name)
               selector))
           patterns)
          (= :include)))))
