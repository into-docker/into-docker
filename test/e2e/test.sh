#!/bin/sh

set -eu

TAG="$1"
WORKDIR=$(dirname "$0")
BUILD="lein run -m into.main build"
BUILDER_IMAGE="into-docker-e2e-test:$TAG"
TARGET_IMAGE="into-docker-e2e-target:$TAG"

build_and_check() {
    $BUILD -v \
        "$@" \
        -t "$TARGET_IMAGE" \
        "$BUILDER_IMAGE" "$WORKDIR"
    test "x$(docker run --rm "$TARGET_IMAGE")" = "xtest.sh"
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


# 1. Create Builder Image
docker build --rm -t "${BUILDER_IMAGE}" -f $WORKDIR/Dockerfile $WORKDIR

# 2. Build the image
set -x
rm -f "$WORKDIR/cache.tar.gz"
# build_and_check --cache $WORKDIR/cache.tar.gz
# build_and_check --cache $WORKDIR/cache.tar.gz

# 3. Build the artifacts
build_artifacts_and_check
