# An approach to reusable build environments

- **Version**: 1.0.1
- **Authors**: Yannick Scherer
- **Created on**: 03.05.2020
- **Last updated on**: 24.05.2021

## Introduction

### Build Process Duplication

Nearly every programming language has a set of well-known build tools and
processes. Running them as part of automation results in a significant amount of
duplication across different projects when it comes to packaging and publishing
build artifacts. Furthermore, as with all duplication, it becomes tedious to
propagate improvements (be it in functionality, efficiency or security).

Containerised environments can address this problem; build containers then
provide all the required dependencies, and sometimes even go one step further,
offering up scripts to execute the recommended build steps.

Consider the NodeJS landscape, as one of many, with the `npm` CLI tool as a
possible participant in the build process: `npm install` will fetch the
dependencies, `npm run build` might compile/transpile the application. All that
is needed to make this reusable is a NodeJS based container with `npm` and a
script that contains `npm install && npm run build`.

There are many ways to use this container - hardly ever, however, as a black
box. In CI systems it could be specified as the environment to run commands in,
with one of those commands being the call to the build script.

```yaml
build:
  image: internal/builder:v1
  script: /opt/builder/build
```

When writing a local `Dockerfile` it could be the basis for the image:

```dockerfile
FROM internal/builder:v1 AS builder
WORKDIR /opt/sources
COPY . .
RUN /opt/builder/build
```

Removing knowledge about the image internals helps make it reusable and
packageable. CircleCI offers, for example, [Orbs][circleci-orbs] as units of
build logic, and even plain Docker images can achieve something similar using
[`ONBUILD`][docker-onbuild] instructions.

[circleci-orbs]: https://circleci.com/orbs/
[docker-onbuild]: https://docs.docker.com/engine/reference/builder/#onbuild

### From Builder to Runner

However, here is the next point where processes diverge since there are many
ways to run or publish an application. Static web pages only need a web server,
NodeJS services need a `node` installation. Often, it seems that latest here a
custom `Dockerfile` is needed:

```dockerfile
FROM nginx:alpine
COPY ./dist /usr/share/nginx/html

# OR (for multi-stage builds):
COPY --from=builder /opt/sources/dist /usr/share/nginx/html
```

It becomes important to ensure compatibility between the build result and the
runner environment; containerised environments won't protect you from choosing
the wrong one. There are documentation-based approaches ("If you use this build
environment, you can use the following execution environments: ...") but those
are rarely verifiable or enforceable.

### Fat Environments

Consider the way that [`s2i`][s2i] handles the situation, by including the build
tooling in the runner environment. It's a fair assumption that if an application
is built within a given environment it will be executable in said environment.
There won't be version conflicts and it can be guaranteed that no necessary
build results are forgotten to be propagated to the runner environment, since
they are already there.

In practice, issues arise with this approach, some minor and some more severe.
Container image size is, for example, naturally larger than for specialised
multi-stage Docker builds; however, this is usually only a concern during the
first pull. More relevant for running such an application is the increased image
surface, effected by a variety of tooling and libraries that come with their own
considerations and security implications.

And finally, there's the developer experience, not for the end-users but the
creators of those build environments. Reusability is _lost_ in `s2i` since you
need to bring together two different sets of tools. To illustrate: Should one
rely on a NodeJS base image and install an Nginx web server? Or should one rely
on an Nginx base image and install NodeJS? Whatever is chosen, an explicit way
to install one of those has to be devised, and most likely duplicated.

[s2i]: https://github.com/openshift/source-to-image

### Lean & Reusable

Publishing an application is often thought of as a _pipeline_ of distinct steps,
each one having exactly one purpose and relying on only the tooling needed to
achieve said purpose. Maintaining this separation, and adding a well-defined way
of connecting such steps, should make every single one more reusable while
keeping them lean and their footprint small.

While there might be a way to create generic chains of build steps (i.e.
"precompile to Java", "compile to JAR", "optimise the JAR", "assemble into a JRE
image"), a lot of care needs to go into devising it and a lot of hidden
complexity might arise. For this reason, the approach outlined in this document
focuses on a two-phase approach instead of the generic case:

1. **Build** runnable artifacts inside the builder environment, including all
   pre- and post-processing steps.
2. **Assemble** those artifacts inside the runner environment, moving it to
   well-known locations.

The builder and runner environments are distinct and specialised, with one main
hope being that a lot of runner images will be standard-issued (like
`nginx:alpine`) instead of needing additional modification.

All necessary information and build/assemble logic is contained inside the
builder image. To this end, heavy use is made of Docker-specific functionality,
including image annotations ("labels"), the ability to extract and inject
filesystem resources, as well as the ability to snapshot running containers into
new images.

## Design

### Overview

Creating a runnable container image using a **`builder-image`** (which contains
a _build script_ and an _assemble script_), **`source-path`** and a
**`target-image`** is done via the following basic flow:

1. Pull the **`builder-image`** and inspect it, inferring the `runner-image` to
   use.
1. Pull the `runner-image`.
1. Start a container, the `builder`, using the `builder-image`.
1. Start a container, the `runner`, using the `runner-image`.
1. Transfer the assemble script from the `builder` container to the `runner`
   container.
1. Inject the contents of **`source-path`** into the `builder` container, at a
   `src` path.
1. Call the build script inside the `builder` container, supplying `src` and the
   desired output directory `artifacts` for build artifacts as environment
   variables.
1. Extract the contents of `artifacts` from the `builder` container and inject
   them into the `runner` container.
1. Call the assemble script inside the `runner` container, supplying the
   `artifacts` directory as an environment variable.
1. Commit the container as **`target-image`**.
1. Stop all containers.

This basic flow can be adjusted to introduce functionality like caching or
artifact extraction.

### Builder Images

To keep configuration effort low, build and assemble scripts are expected in
well-known locations. Necessary configuration options, most notably the name of
the runner image to use, is provided using image annotations/labels.

#### Annotations

| Annotation                          | Required | Description                                                                                          | Example Value            |
| ----------------------------------- | :------: | ---------------------------------------------------------------------------------------------------- | ------------------------ |
| `org.into-docker.runner-image`      |   Yes    | Runner image to inject artifacts into.                                                               | `openjdk:11-jre`         |
| `org.into-docker.runner-cmd`        |    No    | `CMD` override for the runner image.                                                                 | `java -jar /opt/app.jar` |
| `org.into-docker.runner-entrypoint` |    No    | `ENTRYPOINT` override for the runner image.                                                          | `java -jar /opt/app.jar` |
| `org.into-docker.builder-user`      |    No    | User for running the build container (default is `root`).                                            | `builder`                |
| `org.opencontainers.image.*`        |    No    | [OCI image annotations][oci-annotations] (for builder image [inspection](#builder-image-inspection)) | -                        |

[oci-annotations]: https://github.com/opencontainers/image-spec/blob/master/annotations.md#pre-defined-annotation-keys

#### Files

| Path                       | Required | Description                                                                                       |
| -------------------------- | :------: | ------------------------------------------------------------------------------------------------- |
| `/into/bin/build`          |   Yes    | [Build script](#build-script) to generate artifacts from sources (run in builder container).      |
| `/into/bin/assemble`       |   Yes    | [Assemble script](#assemble-script) to generate artifacts from sources (run in runner container). |
| `/into/cache`              |    No    | File describing relative or absolute builder container paths to be [cached](#caching).            |
| `/into/ignore`             |    No    | File in `.dockerignore` format describing [source exclusions](#source-exclusions).                |
| `/into/profiles/{profile}` |    No    | Files describing [build profile](#build-profiles) environment variables.                          |
| `/into/Dockerfile`         |    No    | The Dockerfile used to build the builder image.                                                   |

#### Security Considerations

The execution of the build script is the only thing dependent on external,
user-provided input. This means that it could technically alter the contents of
the `/into/cache` file (potentially hugely increasing the amount of data that is
cached) or the `/into/bin/assemble` script (potentially altering the execution
environment). To avoid this kind of interference, all files MUST be read and
cached before any sources are injected into the bulder container.

Builder images SHOULD provide a non-root `builder-user` that is used to execute
the build script. This user will need write access to all paths that are
supposed to be cached, as well as any others involved in the build process.

The assemble script is executed as `root`. This is done due to the desire to
reuse standard-issued runner images (like `nginx:alpine`) that are rarely
capable of running/building as non-root users, and facilitated by full control
over the assemble script. The assemble script SHOULD thus never execute any
user-provided logic, only move files into the correct places.

Runner images that allow execution as a non-`root` user SHOULD be preferred.

#### Working Directory

The `/tmp` directory MUST exist and be writeable for the `builder-user`. Build
and assemble scripts MUST NOT rely on source and artifact directories residing
in `/tmp`, but rather use the values provided via environment variables.

#### Example

The image created from the following `Dockerfile` will point at a
`<runner-image>` using the appropriate label, and make sure that build logic is
run as a dedicated non-root user `builder`.

```dockerfile
FROM <base image>

ARG COMMIT
ARG BUILD_DATE
ARG USER="builder"

LABEL maintainer="<maintainer>"
LABEL org.opencontainers.image.authors="<maintainer>"
LABEL org.opencontainers.image.licenses="<license>"
LABEL org.opencontainers.image.source="https://github.com/..."
LABEL org.opencontainers.image.url="https://github.com/..."
LABEL org.opencontainers.image.revision="${COMMIT}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

LABEL org.into-docker.builder-user="${USER}"
LABEL org.into-docker.runner-image="<runner-image>"

WORKDIR /into
ENV HOME="/into/home"
RUN useradd -d "${HOME}" -m "${USER}"
COPY into/ .
```

### Build Script

The build script is located at `/into/bin/build` and executed inside the builder
container after sources have been injected. Its purpose is the creation of
artifacts from those sources, which it then moves to a well-known place on the
file system.

#### Environment Variables

| Environment Variable | Description                                   | Example Value |
| -------------------- | --------------------------------------------- | ------------- |
| `$INTO_SOURCE_DIR`   | Directory containing the sources to build.    | `/tmp/src`    |
| `$INTO_ARTIFACT_DIR` | Directory that artifacts should be copied to. | `/tmp/target` |

Additionally, all variables defined in the build profile will be available to
the script.

#### Example

The following could be an example of an `npm`-based build:

```sh
#!/bin/sh

set -eu

cd $INTO_SOURCE_DIR
npm install
npm run build
mv $INTO_SOURCE_DIR/dist/* $INTO_ARTIFACT_DIR
```

### Assemble Script

The assemble script is located at `/into/bin/assemble` inside the builder
container, but transferred and **executed inside the runner container**. Its
purpose is moving artifacts created by the build script to well-known places on
the file system.

#### Environment Variables

| Environment Variable | Description                                | Example Value |
| -------------------- | ------------------------------------------ | ------------- |
| `$INTO_ARTIFACT_DIR` | Directory that artifacts are contained in. | `/tmp/target` |

#### Example

The following could be an example of moving static HTML/CSS/JS to the expected
location inside an Nginx-based runner container:

```sh
#!/bin/sh

set -eu

rm -f /usr/share/nginx/html/index.html
mv $INTO_ARTIFACT_DIR/* /usr/share/nginx/html/
```

### User-provided Environment Variables

**Experimental: This is a preliminary assessment of a feature and might be re-
evaluated and change.**

There are valid use cases where a user would want to provide custom environment
variables, e.g. to allow access to private artifact repositories. However, when
providing such a mechanism, it becomes likely that build scripts themselves
(instead of the tooling they call) will start to rely on them. This quickly
leads to undocumented functionality - which should be avoided.

Since the codebase - or the tooling it relies on - is the thing in need of those
environment variables, there should be a way for it to communicate this fact.
This, in turn, allows for early validation: Don't even start a build if the
respective values are missing.

The following mechanism is used to achieve this:

1. Look for a file `.buildenv` in the source directory that is being built. If
   there is none, there is nothing to do.
2. Treat each line as the name of an environment variable that is required for
   the eventual build to succeed.
3. Read each environment variable provided this way from the build coordinator's
   own environment. If one is missing, fail the build (blank values are
   allowed).
4. Provide each such environment variable to the build container when executing
   its build script.

As you can see, this mechanism imports environment variables from an _outside_
environment into the build container. Note, however, that those will not be
available to the runner container.

### Caching

The approach to caching is a declarative one. The file located at `/into/cache`
inside the builder image describes paths (either relative to the source
directory or absolute) that should be cached. It's the build coordinator's
responsibility to extract these paths, or re-inject it on subsequent builds.

#### File Format

Every line inside the file represents a path to be cached:

- **Wildcards are not allowed.**
- Relative paths are treated as relative to the source directory given by
  `$INTO_SOURCE_DIR`.
- Absolute paths are treated as absolute paths on the filesystem.

#### Cache Considerations

By default, the build coordinator SHOULD NOT employ any caching strategy,
resulting in a fresh build. Caching SHOULD only be used if explicitly desired.

Cache storage is usually external to the build coordinator's responsibility, the
most versatile approach is thus to write the cache to the filesystem and allow
it to be picked up by CI- or user-specific caching. Cache files SHOULD be TAR
archives, with compression optionally enabled.

#### Cache Implementation

A simple way to creation of the cache archive is outlined here. It relies on a
temporary cache path `$CACHE`.

1. For each entry `$P` in the cache specification, compute a hash `$P_HASH = SHA1($P)`.
2. Move the path `$P`, if it exists, to `$CACHE/$P_HASH`.
3. Archive and extract the path `$CACHE`.
4. To restore, inject and extract the archive at path `$CACHE`.
5. For each entry `$P` in the cache specification, compute a hash `$P_HASH = SHA1($P)`.
6. Move the path `$CACHE/$P_HASH`, if it exists, to `$P`.

This allows addition and removal of cache paths while old caches are still
usable for new builds. Only if the builder image changes, could this break, but
this is left in the responsibility of the user.

#### Example

For NodeJS projects build effort impact is significant when caching
`node_modules`. Additional impact could be achieved by caching the NPM cache.

```
node_modules
/into/home/.npm
```

### Source Exclusions

A file located at `/into/ignore` will be used as the basis for
[`.dockerignore`][dockerignore] rules. A local `.dockerignore` will be applied
afterwards.

#### Explicit Inclusion

Due to the nature of the file format, it's still possible to include things in
the build that were previously excluded by either default rules or the file at
`/into/ignore`. See the description of the mechanism [here][dockerignore]:

> Lines starting with ! (exclamation mark) can be used to make exceptions to
> exclusions. The following is an example .dockerignore file that uses this
> mechanism:
>
> ```
> *.md
> !README.md
> ```

[dockerignore]: https://docs.docker.com/engine/reference/builder/#dockerignore-file

#### Example

```dockerignore
logs
*.log
npm-debug.log*
yarn-debug.log*
yarn-error.log*
lerna-debug.log*
node_modules/
jspm_packages/
```

### Build Profiles

Build profiles are files with environment variables located at
`/into/profiles/`, which each file being named like the profile it represents.
One of them is applied when calling the build script.

#### Rationale

Often, the core and flow of the build logic for a specific class of applications
is universally applicable - there are just some small differences for specific
use cases:

- Tooling, e.g. `npm` vs. `yarn`.
- Guarantees, e.g. `npm install` vs. `npm ci`.
- Checks, e.g. security audit before build.
- ...

Creating a separate static builder image for each variant could be considered
overkill. On the other end of the spectrum, allowing the user to override the
build script ([as `s2i` does][s2i-overrides]) gives up control over the build
process and can lead to incompatibilities down the road.

An environment-variable based approach is thus employed, with build profiles
describing **full sets of environment variables**. There is no notion of
inheritance or user customisation and one build profile is guaranteed to result
in a consistent build flow.

[s2i-overrides]: https://github.com/openshift/source-to-image/blob/master/docs/builder_image.md#s2i-scripts

#### Default Profile

The default profile, used when none is explicitly specified, is `default`,
located at `/into/profiles/default`.

#### File Format

Every line in a build profile is a `ENV=VALUE` entry:

- `ENV` and `VALUE` are taken verbatim, quotes around either will not be removed.
- Multi-line values are not allowed.

#### Example

The following could be the `default` profile for a `npm`-based project:

```env
INSTALL_COMMAND=npm install
BUILD_COMMAND=npm run build
```

The following could be the `ci` profile for a `npm`-based project:

```env
INSTALL_COMMAND=npm ci
BUILD_COMMAND=npm run build
```

Note how both `INSTALL_COMMAND` and `BUILD_COMMAND` have to be repeated in the
second profile since there is no notion of inheritance or overriding.

## Outlook

### Builder Image Inspection

Due to the labels provided by the builder image, it should be possible to
provide an overview over:

- OCI information:
  - Version
  - Image URL
  - Source URL (+ Revision)
  - Documentation URL
  - Authors
  - License
- Builder information:
  - Builder user
  - Build profiles
  - Ignore file
  - Cache paths
  - Scripts + Dockerfile
- Runner information:
  - Runner image name
  - Runner CMD or ENTRYPOINT

This information could be collected and exposed as e.g. JSON, allowing secondary
tooling to use it for documentation or validation.

### Ejecting a multi-stage Dockerfile

When builder images follow some `Dockerfile` conventions (and also contain their
own `Dockerfile`) it becomes completely feasible to produce a multi-stage
`Dockerfile` achieving the same result:

```dockerfile
... include builder image Dockerfile here ...

USER <builder-user>
WORKDIR /tmp/src
COPY . .
RUN export INTO_SOURCE_DIR=/tmp/src && \
    export INTO_ARTIFACT_DIR=/tmp/artifacts && \
    mkdir $INTO_ARTIFACT_DIR && \
    /into/build

FROM <runner-image>
COPY --from=0 /tmp/artifacts     /tmp/artifacts
COPY --from=0 /into/bin/assemble /tmp/assemble
RUN export INTO_ARTIFACT_DIR=/tmp/artifacts && \
    /tmp/assemble
```

Build and assemble script need to be extracted from the builder image and moved
to a location where the `Dockerfile` can pick them up. This location is not
necessarily easy to infer which is why, by convention, they should reside in a
directory `into` and be included using `COPY into/ .`.

As a side-note, instead of including the original `Dockerfile`, a `FROM <builder-image>` clause could be used, in which case the above `Dockerfile`
should _just work_.
