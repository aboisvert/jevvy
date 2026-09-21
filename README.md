# jevvy

Run CSV rows through [Jev](https://docs.typesafe.ai/) with YAML-defined questions. Each row becomes JSON state sent to the TypeSafe API; results are written as extra CSV columns.

Licensed under the [Apache License, Version 2.0](LICENSE).

## Usage Requirements

- Download the **`jevvy`** executable for your platform
- **`TYPESAFE_API_KEY`** from your TypeSafe account

## Quick Start

- Create `myfile.yaml` configuration file containing the desired questions:

```yaml
questions:
  - name: department
    type: choice
    question: Which team should handle this message?
    options:
      - id: billing
        description: Payments and refunds
      - id: sales
        description: purchasing, pricing
      - ...
```

- Export your **`TYPESAFE_API_KEY`**

```bash
export TYPESAFE_API_KEY=your-key-here
```

- Run the **`jevvy`** command:

```bash
jevvy --input myfile.csv
```
## CLI Usage

```text
jevvy --input PATH [--config PATH] [--output PATH] [--concurrency N] [--model NAME] [--max-retries N]
```

| Flag | Description |
|------|-------------|
| `--input` | Input CSV path (required) |
| `--config` | YAML config (default: `{input stem}.yaml`) |
| `--output` | Output CSV (default: `{input stem}-out.csv`) |
| `--concurrency` | Override YAML `concurrency` |
| `--model` | Override YAML `model` |
| `--max-retries` | Override YAML `max_retries` (`0` disables SDK retries on transient API errors) |

**Row order:** The output CSV keeps the same row order as the input. With `concurrency` greater than 1, rows are processed in parallel, but each result is written in input order (not completion order).

**Retries:** Each row’s Jev call retries transient failures (429, 529, 5xx, connection errors) by default — three extra attempts with exponential backoff via [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk). Tune with YAML `max_retries` or `--max-retries`; see [docs/yaml-configuration.md](docs/yaml-configuration.md).

Help:

```bash
jevvy --help
```

The process exits with code **1** if any row fails or config/IO errors occur.

## Environment variables

jevvy reads TypeSafe settings from the environment via [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) (`JevConfig.fromEnv`).

| Variable | Description | Default |
|----------|-------------|---------|
| `TYPESAFE_API_KEY` | API key from your [TypeSafe](https://docs.typesafe.ai/) account | *(required)* |
| `TYPESAFE_BASE_URL` | TypeSafe API base URL | `https://api.typesafe.ai` |
| `TYPESAFE_DEFAULT_MODEL` | Default Jev model when YAML `model` and `--model` are not set | `jev-latest` |

Example:

```bash
export TYPESAFE_API_KEY=your-key-here
export TYPESAFE_DEFAULT_MODEL=jev-latest   # optional
export TYPESAFE_BASE_URL=https://api.typesafe.ai   # optional
```

Per-run model overrides still apply: YAML `model`, then CLI `--model` (see [Configuration](#configuration)).

## Configuration

YAML defines batch settings and one or more Jev questions (`noul`, `choice`, or `score`). Full schema, precedence rules, CSV/state mapping, output columns, and many examples: **[docs/yaml-configuration.md](docs/yaml-configuration.md)**.

Minimal skeleton:

```yaml
concurrency: 2

questions:
  - name: department
    type: choice
    question: Which team should handle this message?
    options:
      - id: billing
        description: Payments and refunds
      - id: technical
        description: Bugs and integrations
```

Runnable configs: [`examples/`](examples/).

## Examples

| Scenario | Type | Files |
|----------|------|--------|
| Support triage | choice | `support-triage.{csv,yaml}` |
| Content moderation | noul | `content-moderation.{csv,yaml}` |
| Lead scoring | score | `lead-scoring.{csv,yaml}` |

Details and output column names: [examples/README.md](examples/README.md).

Convenience recipes:

```bash
just example-triage
just example-moderation
just example-lead-scoring
```

## Dev Requirements

- **JDK** — [Amazon Corretto 27](https://docs.aws.amazon.com/corretto/) (see [`.tool-versions`](.tool-versions) for asdf)
- **[Scala CLI](https://scala-cli.virtuslab.org/)**
- **`TYPESAFE_API_KEY`** — from your TypeSafe account
- **[just](https://github.com/casey/just)** (optional) — loads `.env` and runs example recipes

## Dev Check

```bash
# In the repo root
echo 'TYPESAFE_API_KEY=your-key-here' > .env

# With just (loads .env automatically)
just example-triage

# Or with Scala CLI
export TYPESAFE_API_KEY=your-key-here
scala-cli run . -- --input examples/support-triage.csv
```

For `examples/support-triage.csv`, config defaults to `examples/support-triage.yaml` and output to `examples/support-triage-out.csv`.

## Development

```bash
just test          # or: scala-cli test .
just run           # show CLI help
```

To build the native binary (GraalVM; slow first build):

```bash
just native        # writes out/jevvy
```

Built with [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) on Scala 3.

## Related links

- [TypeSafe AI / Jev documentation](https://docs.typesafe.ai/)
- [Jev use cases](https://jevtypesafeai.com/use-cases)
- [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk)
