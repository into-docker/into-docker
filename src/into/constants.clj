(ns into.constants)

;; ## Directories

(let [working-directory "/tmp"]
  (def well-known-paths
    {:source-directory       (str working-directory "/src")
     :artifact-directory     (str working-directory "/artifacts")
     :cache-directory        (str working-directory "/cache")
     :working-directory      working-directory

     :build-script           "/into/bin/build"
     :assemble-script        "/into/bin/assemble"
     :assemble-script-runner (str working-directory "/assemble")

     :profile-directory      "/into/profiles"
     :cache-file             "/into/cache"
     :ignore-file            "/into/ignore"}))

(defn path-for
  [k]
  (get well-known-paths k))

;; ## Special Files

(def build-env-file
  ".buildenv")

;; ## Variables

(def source-dir-env "INTO_SOURCE_DIR")
(def artifact-dir-env "INTO_ARTIFACT_DIR")

;; ## Labels

(def builder-user-label
  :org.into-docker.builder-user)

(def runner-image-label
  :org.into-docker.runner-image)

(def runner-cmd-label
  :org.into-docker.runner-cmd)

(def runner-entrypoint-label
  :org.into-docker.runner-entrypoint)

;; ## Ignore Paths

(def default-ignore-paths
  [".git"
   ".github"
   ".gitignore"
   ".hg"
   ".hgignore"
   "Dockerfile"
   ".dockerignore"
   "**/.DS_Store"])
