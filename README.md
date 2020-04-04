# into-docker

**into-docker** lets you build and run applications relying on common frameworks
or build tools without ever having to write another Dockerfile.

## Usage

```sh
into -t <repository:tag> <builder> <directory>
```

## Creating reusable images

Like [`s2i`][s2i], `into` utilises images that already contain all the
dependencies needed to build an application. It will make your source directory
available to those images and call scripts in well-known locations to perform
the task at hand.

[s2i]: https://github.com/openshift/source-to-image

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

### `Dockerfile`

The builder image indicates the runner image using the docker label
`into.v1.runner`.

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

```sh
#!/bin/sh
set -eu

cp -r $INTO_ARTIFACT_DIR/* /usr/share/nginx/html
```

## License

```
MIT License

Copyright (c) 2020 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
