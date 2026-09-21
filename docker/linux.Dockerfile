FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

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

WORKDIR /jevvy-src
COPY project.scala .
COPY src/main ./src/main

RUN scala-cli --power package . --force --output /jevvy \
    && /jevvy 2>&1 | grep -q 'Usage: jevvy'
