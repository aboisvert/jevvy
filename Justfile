# Jev CSV batch CLI — run with `just batch-triage` (requires TYPESAFE_API_KEY in .env)

set shell := ["sh", "-uc"]
set dotenv-load := true
set dotenv-required := true

default:
    @just --list

# Show CLI usage
run:
    scala-cli run . -- batch --help 2>&1 || scala-cli run .

_batch *ARGS:
    scala-cli run . -- {{ARGS}}

# Support triage (choice) example
batch-triage:
    just _batch --config examples/support-triage.yaml --input examples/support-triage.csv --output /tmp/jevvy-support-triage-out.csv

# Content moderation (noul) example
batch-moderation:
    just _batch --config examples/content-moderation.yaml --input examples/content-moderation.csv --output /tmp/jevvy-content-moderation-out.csv

# Lead scoring (score) example
batch-lead:
    just _batch --config examples/lead-scoring.yaml --input examples/lead-scoring.csv --output /tmp/jevvy-lead-scoring-out.csv

test:
    scala-cli test .

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
