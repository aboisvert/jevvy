# syntax=docker/dockerfile:1.6

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
# Match Coursier layout on the host (v1 + jvm siblings under .../coursier).
ENV COURSIER_CACHE=/root/.cache/coursier/v1

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    zlib1g-dev \
    curl \
    ca-certificates \
    gzip \
    && rm -rf /var/lib/apt/lists/*

ARG SCALA_CLI_VERSION=1.15.0
RUN curl -fL "https://github.com/VirtusLab/scala-cli/releases/download/v${SCALA_CLI_VERSION}/scala-cli-x86_64-pc-linux.gz" \
    | gzip -dc > /usr/local/bin/scala-cli \
    && chmod +x /usr/local/bin/scala-cli

# Keep in sync with //> using packaging.graalvmJvmId in project.scala
ARG GRAALVM_JVM_ID=graalvm-oracle:25

WORKDIR /jevvy-src

# Resolve JVM + Maven deps before app sources change (Docker layer cache).
COPY project.scala .
RUN --mount=type=bind,from=coursier-cache,source=.,target=/root/.cache/coursier,rw \
    --mount=type=bind,from=scala-cli-cache,source=.,target=/root/.cache/scala-cli,rw \
    --mount=type=cache,target=/jevvy-src/.scala-build \
    scala-cli --power compile . --jvm ${GRAALVM_JVM_ID}

COPY src/main ./src/main
RUN --mount=type=bind,from=coursier-cache,source=.,target=/root/.cache/coursier,rw \
    --mount=type=bind,from=scala-cli-cache,source=.,target=/root/.cache/scala-cli,rw \
    --mount=type=cache,target=/jevvy-src/.scala-build \
    scala-cli --power package . --force --output /jevvy \
    && /jevvy 2>&1 | grep -q 'Usage: jevvy'
