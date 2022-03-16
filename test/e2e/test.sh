#!/bin/sh

set -eu

TAG="$1"
WORKDIR=$(dirname "$0")
BUILD=${2:-"lein run -m into.main build"}
BUILDER_IMAGE="into-docker-e2e-test:$TAG"
TARGET_IMAGE="into-docker-e2e-target:$TAG"

start_group() {
    if [ "x$CI" = "xtrue" ]; then
        echo "::group::$@"
    fi
}

end_group() {
    if [ "x$CI" = "xtrue" ]; then
        echo "::endgroup::"
    fi
}

start_group_and_build() {
    start_group "$BUILD $@"
    $BUILD "$@"
}

build_and_check() {
    start_group_and_build -v \
        "$@" \
        -t "$TARGET_IMAGE" \
        "$BUILDER_IMAGE" "$WORKDIR"
    test "x$(docker run --rm "$TARGET_IMAGE")" = "xtest.sh"
    docker rmi "$TARGET_IMAGE"
    end_group

}

build_artifacts_and_check() {
    ARTIFACT_DIR="$WORKDIR/../../target/artifacts"
    mkdir -p "$ARTIFACT_DIR"
    start_group_and_build -v \
        --write-artifacts "$ARTIFACT_DIR" \
        "$BUILDER_IMAGE" \
        "$WORKDIR"
    test "x$(ls "$ARTIFACT_DIR")" = "xtest.sh"
    end_group
}


# Create Builder Image
docker build --rm -t "${BUILDER_IMAGE}" -f $WORKDIR/Dockerfile $WORKDIR

# Provide .buildenv
export SECRET_USERNAME="username"
export SECRET_PASSWORD="password"

# Build the image
build_and_check
build_and_check --no-volumes

# Build the image for another platform
build_and_check --platform linux/arm64

# Build the image using a cache
rm -f "$WORKDIR/cache.tar.gz"
build_and_check --cache $WORKDIR/cache.tar.gz
build_and_check --cache $WORKDIR/cache.tar.gz

# Build only the artifacts
build_artifacts_and_check
