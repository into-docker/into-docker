(ns into.docker.progress
  (:require [into.log :as log]))

(defn- reset-lines
  "Moves n lines up."
  [n]
  (when (pos? n)
    (print (str (char 27) "[" n "A"))))

(defn- add-progress
  [state {:keys [id] :as layer}]
  (let [{:keys [written layers]} (assoc-in state [:layers id] layer)]
    {:reset   written
     :written (count layers)
     :layers  layers}))

(defn- print-layer-progress
  [id {:keys [status progress]}]
  (println "[into]   " (str (char 27) "[K" (format "%s %-11s %s" id status progress))))

(defn progress-printer
  "Create a stateful function that prints a progress report. It takes layer
   progress information as returned e.g. by `ImageCreate`, containing `:status`,
   `:id`, `:progress` keys."
  []
  (if (log/has-level? :info)
    (let [state (volatile! {:reset 0, :written 0, :layers {}})]
      (fn [layer]
        (when (:progress layer)
          (let [{:keys [reset layers]} (vswap! state add-progress layer)]
            (reset-lines reset)
            (doseq [[id layer] (sort-by key layers)]
              (print-layer-progress id layer))))))
    (fn [_] nil)))
