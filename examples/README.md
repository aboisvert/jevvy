# Jev batch examples

Each scenario matches a [Jev use case](https://jevtypesafeai.com/use-cases). Every CSV row is sent to Jev as a JSON object (column name → string value).

Requires `TYPESAFE_API_KEY` in the environment (or `.env` when using `just`).

## Support triage (choice)

Routes inbound support messages to a team.

```bash
scala-cli run . -- \
  --config examples/support-triage.yaml \
  --input examples/support-triage.csv \
  --output /tmp/support-triage-out.csv
```

Output columns appended: `department_choice`, `department_confidence`, `jev_error`.

## Content moderation (noul)

Estimates whether UGC should be blocked.

```bash
scala-cli run . -- \
  --config examples/content-moderation.yaml \
  --input examples/content-moderation.csv \
  --output /tmp/content-moderation-out.csv
```

Output columns: `should_block_probability`, `jev_error`.

## Lead scoring (score)

Scores sales readiness on a cold / warm / hot scale.

```bash
scala-cli run . -- \
  --config examples/lead-scoring.yaml \
  --input examples/lead-scoring.csv \
  --output /tmp/lead-scoring-out.csv
```

Output columns: `sales_readiness_score`, `sales_readiness_nearest_label`, `jev_error`.

Or use convenience recipes from the repo root: `just batch-triage`, `just batch-moderation`, `just batch-lead`.
