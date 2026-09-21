# jevvy CSV CLI — run with `just example-triage` (requires TYPESAFE_API_KEY in .env)

set shell := ["sh", "-uc"]
set dotenv-load := true
set dotenv-required := true

default:
    @just --list

# Show CLI usage
run:
    scala-cli run . -- --help 2>&1 || scala-cli run .

_run *ARGS:
    scala-cli run . -- {{ARGS}}

# Support triage (choice) example
example-triage:
    just _run --config examples/support-triage.yaml --input examples/support-triage.csv --output /tmp/jevvy-support-triage-out.csv

# Content moderation (noul) example
example-moderation:
    just _run --config examples/content-moderation.yaml --input examples/content-moderation.csv --output /tmp/jevvy-content-moderation-out.csv

# Lead scoring (score) example
example-lead-scoring:
    just _run --config examples/lead-scoring.yaml --input examples/lead-scoring.csv --output /tmp/jevvy-lead-scoring-out.csv

test:
    scala-cli test .

# Reformat all Scala sources (main and test) with scalafmt
fmt:
    scala-cli fmt .

# Build GraalVM native binary at out/jevvy (slow; downloads Oracle GraalVM via Coursier).
# Override JVM: GRAALVM_JVM_ID=system just native, or `just native graal_jvm_id=system`
native graal_jvm_id=env_var_or_default('GRAALVM_JVM_ID', ''):
    #!/usr/bin/env sh
    set -eu
    jvm_id='{{ graal_jvm_id }}'
    if [ -n "$jvm_id" ]; then
      exec scala-cli --power package . --force --graalvm-jvm-id "$jvm_id"
    else
      exec scala-cli --power package . --force
    fi

# Host paths for Coursier / Scala CLI caches (bind-mounted into linux Docker builds).
_docker_cache_dirs:
    #!/usr/bin/env sh
    set -eu
    case "$(uname -s)" in
      Darwin)
        coursier="${COURSIER_CACHE_HOST:-$HOME/Library/Caches/Coursier}"
        scala_cli="${SCALA_CLI_CACHE_HOST:-$HOME/Library/Caches/ScalaCli}"
        ;;
      Linux)
        base="${XDG_CACHE_HOME:-$HOME/.cache}"
        coursier="${COURSIER_CACHE_HOST:-$base/coursier}"
        scala_cli="${SCALA_CLI_CACHE_HOST:-$base/scala-cli}"
        ;;
      *)
        echo "Unsupported OS for Docker cache dirs: $(uname -s)" >&2
        exit 1
        ;;
    esac
    mkdir -p "$coursier" "$scala_cli"
    printf '%s\n' "$coursier" "$scala_cli"

# GraalVM native linux/amd64 binary in a local image (for release or debugging).
docker-linux-build:
    #!/usr/bin/env sh
    set -eu
    cache_dirs="$(just _docker_cache_dirs)"
    coursier="$(printf '%s\n' "$cache_dirs" | sed -n '1p')"
    scala_cli="$(printf '%s\n' "$cache_dirs" | sed -n '2p')"
    DOCKER_BUILDKIT=1 docker build --platform linux/amd64 \
      -f docker/linux.Dockerfile \
      --build-context "coursier-cache=$coursier" \
      --build-context "scala-cli-cache=$scala_cli" \
      -t jevvy-linux-build .

# Build versioned release binaries (see VERSION). macOS arm64: host + Docker linux/amd64; Linux x86_64: host only.
release:
    #!/usr/bin/env sh
    set -eu
    VERSION="$(tr -d ' \n\r' < VERSION)"
    if [ -z "$VERSION" ]; then
      echo "VERSION file is empty or missing" >&2
      exit 1
    fi
    smoke_test() {
      if ! "$1" 2>&1 | grep -q 'Usage: jevvy'; then
        echo "Smoke test failed for $1" >&2
        exit 1
      fi
    }
    mkdir -p out
    case "$(uname -s)-$(uname -m)" in
      Darwin-arm64)
        osx_out="out/jevvy-osx-arm64-v${VERSION}-bin"
        scala-cli --power package . --force --output "$osx_out"
        smoke_test "$osx_out"
        linux_out="out/jevvy-linux-x64-v${VERSION}-bin"
        just docker-linux-build
        cid="$(docker create jevvy-linux-build)"
        docker cp "$cid:/jevvy" "$linux_out"
        docker rm "$cid" >/dev/null
        chmod +x "$linux_out"
        docker run --rm --platform linux/amd64 jevvy-linux-build /jevvy 2>&1 | grep -q 'Usage: jevvy'
        echo "Wrote $osx_out and $linux_out"
        ;;
      Linux-x86_64|Linux-amd64)
        linux_out="out/jevvy-linux-x64-v${VERSION}-bin"
        scala-cli --power package . --force --output "$linux_out"
        smoke_test "$linux_out"
        echo "Wrote $linux_out"
        ;;
      *)
        echo "Unsupported platform for release: $(uname -s) $(uname -m)" >&2
        exit 1
        ;;
    esac
