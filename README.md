# jevvy

Batch CSV rows through [Jev](https://docs.typesafe.ai/) with YAML-defined questions. Each row becomes JSON state sent to the TypeSafe API; results are written as extra CSV columns.

Licensed under the [Apache License, Version 2.0](LICENSE).

## Requirements

- **JDK** — [Amazon Corretto 27](https://docs.aws.amazon.com/corretto/) (see [`.tool-versions`](.tool-versions) for asdf)
- **[Scala CLI](https://scala-cli.virtuslab.org/)**
- **`TYPESAFE_API_KEY`** — from your TypeSafe account
- **[just](https://github.com/casey/just)** (optional) — loads `.env` and runs example recipes

## Quick start

```bash
# In the repo root
echo 'TYPESAFE_API_KEY=your-key-here' > .env

# With just (loads .env automatically)
just batch-triage

# Or with Scala CLI directly
export TYPESAFE_API_KEY=your-key-here
scala-cli run . -- --input examples/support-triage.csv
```

For `examples/support-triage.csv`, config defaults to `examples/support-triage.yaml` and output to `examples/support-triage-out.csv`.

## CLI

```text
batch --input PATH [--config PATH] [--output PATH] [--concurrency N] [--model NAME]
```

| Flag | Description |
|------|-------------|
| `--input` | Input CSV path (required) |
| `--config` | YAML config (default: `{input stem}.yaml`) |
| `--output` | Output CSV (default: `{input stem}-out.csv`) |
| `--concurrency` | Override YAML `concurrency` |
| `--model` | Override YAML `model` |

Help:

```bash
scala-cli run . -- batch --help
```

The process exits with code **1** if any row fails or config/IO errors occur.

## Configuration

YAML defines batch settings and one or more Jev questions. Supported question types: **`noul`**, **`choice`**, and **`score`**.

Top-level fields (all optional except `questions`):

- `concurrency` — parallel row processing
- `delay_ms` — delay between requests
- `model` — Jev model name
- `timeout_seconds` — per-request timeout
- `questions` — list of named questions (see examples)

Example skeleton:

```yaml
concurrency: 2

questions:
  - name: department
    type: choice
    question: Which team should handle this message?
    options:
      - id: billing
        description: Payments and refunds
```

See [`examples/`](examples/) for full configs.

## Examples

| Scenario | Type | Files |
|----------|------|--------|
| Support triage | choice | `support-triage.{csv,yaml}` |
| Content moderation | noul | `content-moderation.{csv,yaml}` |
| Lead scoring | score | `lead-scoring.{csv,yaml}` |

Details and output column names: [examples/README.md](examples/README.md).

Convenience recipes:

```bash
just batch-triage
just batch-moderation
just batch-lead
```

## Development

```bash
just test          # or: scala-cli test .
just run           # show CLI help
```

Native binary (GraalVM; slow first build):

```bash
just native        # writes out/jevvy
```

Built with [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk) on Scala 3.

## Related links

- [TypeSafe AI / Jev documentation](https://docs.typesafe.ai/)
- [Jev use cases](https://jevtypesafeai.com/use-cases)
- [scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk)
