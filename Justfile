# Jev / TypeSafe example — run with `just run`

set shell := ["sh", "-uc"]
set dotenv-load := true
set dotenv-required := true

default:
    @just --list

# Run the Jev SDK example (requires TYPESAFE_API_KEY in .env)
run:
    scala-cli run .
