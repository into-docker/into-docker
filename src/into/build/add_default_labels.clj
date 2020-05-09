(ns into.build.add-default-labels
  (:require [into.utils.version :as v]))

(def ^:private default-labels
  {"org.into-docker.version"  (v/current-version)
   "org.into-docker.revision" (v/current-revision)
   "org.into-docker.url"      "https://github.com/into-docker/into-docker"})

(defn- image-labels
  [{:keys [builder-image runner-image]}]
  {"org.into-docker.builder-image" (str builder-image)
   "org.into-docker.runner-image"  (str runner-image)})

(defn- clear-labels
  "Sets those labels that should be cleared. For example, the maintainer of the
   runner image is not the one of the created image."
  [_]
  {"maintainer" ""})

(defn run
  [data]
  (if (:target-image data)
    (let [labels (merge default-labels
                        (image-labels data)
                        (clear-labels data))]
      (update-in data [:target-image :labels] merge labels))
    data))
