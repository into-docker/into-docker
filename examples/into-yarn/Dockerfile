FROM node:alpine
LABEL maintainer="Yannick Scherer <yannick.scherer@gmail.com>"
LABEL into.v1.runner="nginx:alpine"

WORKDIR /into
COPY assemble build index.html ./
RUN echo package.json > ignore
