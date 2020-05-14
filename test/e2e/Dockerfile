FROM ubuntu
ARG USER="builder"
LABEL org.into-docker.builder-user="${USER}"
LABEL org.into-docker.runner-image="alpine"
LABEL org.into-docker.runner-entrypoint="ls /opt/app"

WORKDIR /into
ENV HOME="/into/home" LEIN_JVM_OPTS="-Duser.home=/into/home"
RUN useradd -d "${HOME}" -m "${USER}"
COPY into/ .
