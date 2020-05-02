(ns into.constants)

(let [working-directory "/tmp"]
  (def well-known-paths
    {:source-directory   (str working-directory "/src")
     :artifact-directory (str working-directory "/artifacts")
     :cache-directory    (str working-directory "/cache")
     :working-directory  working-directory

     :build-script       "/into/bin/build"
     :assemble-script    "/into/bin/assemble"

     :profile-directory  "/into/profiles"
     :cache-file         "/into/cache"
     :ignore-file        "/into/ignore"}))
