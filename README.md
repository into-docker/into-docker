# into-docker

[![CI](https://github.com/into-docker/into-docker/workflows/CI/badge.svg)](https://github.com/into-docker/into-docker/actions?query=workflow%3ACI)
[![Release](https://img.shields.io/github/v/release/into-docker/into-docker?include_prereleases&sort=semver)](https://github.com/into-docker/into-docker/releases/latest)

**into-docker** lets you build and run applications relying on common frameworks
or build tools without ever having to write another Dockerfile. It allows you to
bundle up your build environments and processes for others to reuse.

This tool is inspired by [`s2i`][s2i] and shares some concepts, ideas and goals.
However, it targets one specific use case, the classic
[multi-stage][multi-stage] build where artifacts are created in a _fat_ build
environment before being injected into a _leaner_ runner environment.

## Goals

- **Minimal-configuration builds**: Rather than providing infinite ways to
  configure your builds, we encourage the use of defaults and rely on
  convention over configuration when creating builder images.
- **Promote best practices**: Instead of creating a multitude of Docker images
  of varying quality, developers can benefit from the work of the community in
  a non-copy/paste fashion. This includes [image labels][oci] and automatic
  application of [ignore rules][di].
- **Reproducible builds**: By packaging your build environment as a Docker
  image, it can be versioned just like your source code. The same builder image
  should, given a specific set of inputs, always produce the same output.
- **Small footprint**: Images created by this tool will only differ in one layer
  from the base image, reducing bandwidth usage when pushing and pulling similar
  images.
- **Compliance**: As maintainer of a build platform you can curate a list of
  official build environments. Not only do platform users no longer have to
  write their own Dockerfile - they easily benefit from updates and patches
  to the build environment.
- **Control the execution environment**: Everything above also applies to runner
  images, allowing platform users to benefit from improvements to the execution
  environment while complying with regulations and best practices.

## Usage

**Run Build**

To build local sources using an into-docker builder image use the `build` command
and supply the desired target image name and tag:

```sh
into build -t <name:tag> <builder> [<path>]
```

Learn how to [create your own builder image][builder-images].

**Profiles**

Builder images can supply multiple _build profiles_ to allow for fine-tuning of
the build process. This could, for example, allow you to use the same builder
image for your React application whether you're relying on `npm` or `yarn`.

You can choose a build profile using the `-p`/`--profile` command line argument:

```sh
into build -t <name:tag> -p <profile> <builder>
```

**Caching**

Repeated builds of the same codebase can usually be sped up by caching intermediate
build results, most often downloaded dependencies. By default, `into` runs a fresh
build each time but by supplying the `--cache` command line argument a cache archive
(tar) will be created at the desired path.

```sh
into build -t <name:tag> --cache <path> <builder>
```

Subsequent builds will use the archive (if it exists) to seed the builder container.

[di]: https://codefresh.io/docker-tutorial/not-ignore-dockerignore-2/
[oci]: https://github.com/opencontainers/image-spec/blob/master/annotations.md
[s2i]: https://github.com/openshift/source-to-image
[multi-stage]: https://docs.docker.com/develop/develop-images/multistage-build/
[builder-images]: doc/BUILDER_IMAGES.md

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
