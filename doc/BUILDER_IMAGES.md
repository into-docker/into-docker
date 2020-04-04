# Builder Images

## Overview

A builder image supplies a few files:

- `/into/build` to build artifacts,
- `/into/assemble` to move artifacts to places they can be run from,
- `/into/ignore` to supply exclusions to source files.

When running `into`, the following will happen:

1. Start the builder image.
2. Copy sources into the builder container (heed `/into/ignore`).
3. Run `/into/build` to build the sources and create artifacts.
4. Start the runner image.
5. Copy artifacts and `/into/assemble` to the runner container.
6. Run `/into/assemble` to prepare the runner.
7. Commit the runner container.

## Files

### `Dockerfile`

The builder image needs to provide build dependencies, the `/into/*` files, as
well as the following labels:

| Label                | Required | Description                            | Example Value            |
| -------------------- | -------- | -------------------------------------- | ------------------------ |
| `into.v1.runner`     | Yes      | Runner image to inject artifacts into. | `openjdk:11-jre`         |
| `into.v1.runner.cmd` | No       | `CMD` override for the runner image.   | `java -jar /opt/app.jar` |

**Example**

```dockerfile
FROM node:alpine
LABEL into.v1.runner=nginx:alpine
WORKDIR /into
COPY build assemble ignore .
RUN chmod -R a+rw /into
```

### `/into/ignore`

This is a file that, like `.dockerignore`, prevents files from being injected
into the builder container. In additon, you can have an actual `.dockerignore`
file in your source folder to add more exclusions.

**Example**

```dockerignore
node_modules
```

### `/into/build`

The build script gets called without parameters, but with the following
environment variables available:

| Environment Variable | Description                                   | Example Value |
| -------------------- | --------------------------------------------- | ------------- |
| `$INTO_SOURCE_DIR`   | Directory containing the sources to build.    | `/tmp/src`    |
| `$INTO_ARTIFACT_DIR` | Directory that artifacts should be copied to. | `/tmp/target` |

As you can intuit, the build script's purpose is taking the sources from
`$INTO_SOURCE_DIR` and run the build logic before pushing any resulting
artifacts to `$INTO_ARTIFACT_DIR`.

**Example**

```sh
#!/bin/sh
set -eu

cd "$INTO_SOURCE_DIR"
yarn
yarn build
mv -r ./build/* "$INTO_ARTIFACT_DIR"
```

### `/into/assemble`

The assemble script gets called without parameters **inside the runner image**,
with the following environment variables available:

| Environment Variable | Description                                | Example Value |
| -------------------- | ------------------------------------------ | ------------- |
| `$INTO_ARTIFACT_DIR` | Directory that artifacts are contained in. | `/tmp/target` |

The assemble script's purpose is to move artifacts to well-known paths for the
runner image to pick them up.

**Example**

```sh
#!/bin/sh
set -eu

cp -r $INTO_ARTIFACT_DIR/* /usr/share/nginx/html
```
