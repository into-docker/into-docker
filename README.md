# into-docker

**into-docker** lets you build and run applications relying on common frameworks
or build tools without ever having to write another Dockerfile.

## Usage

```sh
into -t <repository:tag> <builder> <directory>
```

## Overview

Like [`s2i`][s2i], `into` utilises images that already contain all the
dependencies needed to build an application. It will make your source directory
available to those images and call scripts in well-known locations to perform
the task at hand.

Builder images reference a runner image that their artifacts will be supplied
to. Effectively, `into` thus achieves the same as a multi-stage Docker build.

Please check out the [builder image walkthrough](doc/BUILDER_IMAGES.md) for
details and how to create your own builder images.

[s2i]: https://github.com/openshift/source-to-image

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
