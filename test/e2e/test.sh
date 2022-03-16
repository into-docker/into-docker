#!/bin/sh

set -eu

TAG="$1"
CI=${CI:-false}
WORKDIR=$(dirname "$0")
BUILD=${2:-"lein run -m into.main build"}
BUILDER_IMAGE="into-docker-e2e-test:$TAG"
TARGET_IMAGE="into-docker-e2e-target:$TAG"

run_group () {
    if [ "x$CI" = "xtrue" ]; then echo "::group::$1"; fi
    shift
    "$@"
    if [ "x$CI" = "xtrue" ]; then echo "::endgroup::"; fi

}

build_and_check() {
    $BUILD -v \
        "$@" \
        -t "$TARGET_IMAGE" \
        "$BUILDER_IMAGE" "$WORKDIR"
    test "x$(docker run --rm "$TARGET_IMAGE")" = "xtest.sh"
    docker rmi "$TARGET_IMAGE"
}

build_artifacts_and_check() {
    ARTIFACT_DIR="$WORKDIR/../../target/artifacts"
    mkdir -p "$ARTIFACT_DIR"
    $BUILD -v \
        --write-artifacts "$ARTIFACT_DIR" \
        "$BUILDER_IMAGE" \
        "$WORKDIR"
    test "x$(ls "$ARTIFACT_DIR")" = "xtest.sh"
}

# Create Builder Image
docker build --rm -t "${BUILDER_IMAGE}" -f $WORKDIR/Dockerfile $WORKDIR

# Provide .buildenv
export SECRET_USERNAME="username"
export SECRET_PASSWORD="password"

# Build the image
run_group "standard build"              build_and_check
run_group "standard build (no volumes)" build_and_check --no-volumes
run_group "standard build (platform)"   build_and_check --platform linux/arm64

# Build the image using a cache
rm -f "$WORKDIR/cache.tar.gz"
run_group "build creating cache"        build_and_check --cache $WORKDIR/cache.tar.gz
run_group "build using cache"           build_and_check --cache $WORKDIR/cache.tar.gz

# Build only the artifacts
run_group "build artifacts only"        build_artifacts_and_check
