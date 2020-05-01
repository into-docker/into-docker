# into-docker

[![CI](https://github.com/into-docker/into-docker/workflows/CI/badge.svg)](https://github.com/into-docker/into-docker/actions?query=workflow%3ACI)
[![Release](https://img.shields.io/github/v/release/into-docker/into-docker?include_prereleases&sort=semver)](https://github.com/into-docker/into-docker/releases/latest)
[![Spice Program](https://img.shields.io/badge/spice_program-sponsored-brightgreen.svg?logo=data%3Aimage%2Fpng%3Bbase64%2CiVBORw0KGgoAAAANSUhEUgAAAA4AAAAPCAMAAADjyg5GAAABqlBMVEUAAAAzmTM3pEn%2FSTGhVSY4ZD43STdOXk5lSGAyhz41iz8xkz2HUCWFFhTFFRUzZDvbIB00Zzoyfj9zlHY0ZzmMfY0ydT0zjj92l3qjeR3dNSkoZp4ykEAzjT8ylUBlgj0yiT0ymECkwKjWqAyjuqcghpUykD%2BUQCKoQyAHb%2BgylkAyl0EynkEzmkA0mUA3mj86oUg7oUo8n0k%2FS%2Bw%2Fo0xBnE5BpU9Br0ZKo1ZLmFZOjEhesGljuzllqW50tH14aS14qm17mX9%2Bx4GAgUCEx02JySqOvpSXvI%2BYvp2orqmpzeGrQh%2Bsr6yssa2ttK6v0bKxMBy01bm4zLu5yry7yb29x77BzMPCxsLEzMXFxsXGx8fI3PLJ08vKysrKy8rL2s3MzczOH8LR0dHW19bX19fZ2dna2trc3Nzd3d3d3t3f39%2FgtZTg4ODi4uLj4%2BPlGxLl5eXm5ubnRzPn5%2Bfo6Ojp6enqfmzq6urr6%2Bvt7e3t7u3uDwvugwbu7u7v6Obv8fDz8%2FP09PT2igP29vb4%2BPj6y376%2Bu%2F7%2Bfv9%2Ff39%2Fv3%2BkAH%2FAwf%2FtwD%2F9wCyh1KfAAAAKXRSTlMABQ4VGykqLjVCTVNgdXuHj5Kaq62vt77ExNPX2%2Bju8vX6%2Bvr7%2FP7%2B%2FiiUMfUAAADTSURBVAjXBcFRTsIwHAfgX%2FtvOyjdYDUsRkFjTIwkPvjiOTyX9%2FAIJt7BF570BopEdHOOstHS%2BX0s439RGwnfuB5gSFOZAgDqjQOBivtGkCc7j%2B2e8XNzefWSu%2BsZUD1QfoTq0y6mZsUSvIkRoGYnHu6Yc63pDCjiSNE2kYLdCUAWVmK4zsxzO%2BQQFxNs5b479NHXopkbWX9U3PAwWAVSY%2FpZf1udQ7rfUpQ1CzurDPpwo16Ff2cMWjuFHX9qCV0Y0Ok4Jvh63IABUNnktl%2B6sgP%2BARIxSrT%2FMhLlAAAAAElFTkSuQmCC)](https://spiceprograrm.org)

**into-docker** lets you build and run applications relying on common frameworks
or build tools without ever having to write another Dockerfile. It allows you to
bundle up your build environments and processes for others to reuse.

This tool is inspired by [`s2i`][s2i] and shares some concepts, ideas and goals.
However, it targets one specific use case, the classic
[multi-stage][multi-stage] build where artifacts are created in a _fat_ build
environment before being injected into a _leaner_ runner environment.

## Goals

- **Minimum-configuration builds**: Rather than providing infinite ways to
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

### Run Build

To build local sources using an into-docker builder image use the `build` command
and supply the desired target image name and tag:

```sh
into build -t <name:tag> <builder> [<path>]
```

Learn how to [create your own builder image][builder-images].

### Build Profiles

Builder images can supply multiple _build profiles_ to allow for fine-tuning of
the build process. This could, for example, allow you to use the same builder
image for your React application whether you're relying on `npm` or `yarn`.

You can choose a build profile using the `-p`/`--profile` command line argument:

```sh
into build -t <name:tag> -p <profile> <builder>
```

### Caching

Repeated builds of the same codebase can usually be sped up by caching
intermediate build results like downloaded dependencies. By default, `into` runs
a fresh build each time but by supplying the `--cache` command line argument a
cache archive (tar) will be created at the desired path.

```sh
into build -t <name:tag> --cache <path> <builder>
```

Subsequent builds will use the archive (if it exists) to seed the builder
container.

### Use on CI

Due to the minimal-configuration approach of into-docker, it can be easily used
on the CI environment of your choice. Check out the following pre-packaged build
steps:

- [Github Actions](https://github.com/marketplace/actions/into-docker)

Use the `--ci` flag to direct the CLI tool to use CI-specific assumptions when
building images. This allows you, for example, to use environment variables to
fill image labels.

Check out the `into.build.ci` namespace if you want to add more environments.

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
