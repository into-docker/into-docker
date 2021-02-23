#!/bin/sh

set -eu

TAG="$1"
WORKDIR=$(dirname "$0")
BUILD=${2:-"lein run -m into.main build"}
BUILDER_IMAGE="into-docker-e2e-test:$TAG"
TARGET_IMAGE="into-docker-e2e-target:$TAG"

build_and_check() {
    $BUILD -vv \
        "$@" \
        -t "$TARGET_IMAGE" \
        "$BUILDER_IMAGE" "$WORKDIR"
    test "x$(docker run --rm "$TARGET_IMAGE")" = "xtest.sh"
    docker rmi "$TARGET_IMAGE"
}

build_artifacts_and_check() {
    ARTIFACT_DIR="$WORKDIR/../../target/artifacts"
    mkdir -p "$ARTIFACT_DIR"
    $BUILD -vv \
        --write-artifacts "$ARTIFACT_DIR" \
        "$BUILDER_IMAGE" \
        "$WORKDIR"
    test "x$(ls "$ARTIFACT_DIR")" = "xtest.sh"
}


# Create Builder Image
docker build --rm -t "${BUILDER_IMAGE}" -f $WORKDIR/Dockerfile $WORKDIR

# Build the image
build_and_check
build_and_check --no-volumes

# Build the image using a cache
set -x
rm -f "$WORKDIR/cache.tar.gz"
build_and_check --cache $WORKDIR/cache.tar.gz
build_and_check --cache $WORKDIR/cache.tar.gz

# Build only the artifacts
build_artifacts_and_check
