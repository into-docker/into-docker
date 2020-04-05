(ns into.utils.data)

;; ## Conversion

(defn ->image
  [^String value]
  (let [index (.lastIndexOf value ":")]
    (if (pos? index)
      {:name      (subs value 0 index)
       :tag       (subs value (inc index))
       :full-name value}
      {:name      value
       :tag       "latest"
       :full-name (str value ":latest")})))

;; ## Access

(defn path-for
  [data path-key]
  (get-in data [:paths path-key]))

(defn instance-label
  [data instance-key label]
  (get-in data [:instances instance-key :labels label]))

(defn instance-image
  [data instance-key]
  (get-in data [:instances instance-key :image]))

(defn instance-image-name
  ^String [data instance-key]
  (get-in data [:instances instance-key :image :full-name]))

(defn assoc-instance-container
  ^String [data instance-key container]
  (assoc-in data [:instances instance-key :container] container))

(defn instance-container
  [data instance-key]
  (get-in data [:instances instance-key :container]))
