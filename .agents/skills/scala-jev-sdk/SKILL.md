---
name: scala-jev-sdk
license: Apache-2.0
description: >
  Integrate Jev from Scala using io.github.ticofab scala-jev-sdk: JevClient,
  typed Question and Answer lookup, sttp backends, JevConfig, and Either-based
  JevError handling. Use when adding or changing Scala code that calls Jev,
  wiring TypeSafe API keys, choosing noul/choice/score questions in code,
  effect-agnostic HTTP (Future, blocking Identity, cats-effect), retries, or
  when the user mentions scala-jev-sdk, ticofab/jev, or probabilities for
  routing and gating in Scala. For what to ask and how to design judgments,
  use the typesafe-ai skill and docs.typesafe.ai first.
---

# Jev from Scala (scala-jev-sdk)

[Jev](https://docs.typesafe.ai) is TypeSafe's System One model: it answers typed
questions about application **state** and returns **probabilities**, not prose.
This skill covers the **[scala-jev-sdk](https://github.com/ticofab/scala-jev-sdk)**
client only. For primitives, state shape, confidence policy, and cookbooks, read
the [typesafe-ai](../typesafe-ai/SKILL.md) skill and
[docs.typesafe.ai](https://docs.typesafe.ai/llms.txt).

## Model in one minute

| Primitive | Question | Answer |
| --- | --- | --- |
| **noul** | Is this true? | Calibrated probability in \[0, 1\] |
| **choice** | Which option? | Selected option, full distribution, confidence |
| **score** | Where on this scale? | Probability-weighted position (can sit between levels) |

One request sends one state and any number of named questions; all are answered in
a single round trip. Built for in-app decisions (routing, gating, ranking), not
text generation.

## Setup

**Requirements:** Scala 3.3+ (LTS), JDK 17+. Dependencies: `sttp-client4-core`,
`upickle`. No effect system is bundled—you supply an sttp backend.

**sbt:**

```scala
libraryDependencies += "io.github.ticofab" %% "scala-jev-sdk" % "<version>"
```

Pin the version from [Maven Central](https://central.sonatype.com/) or the SDK
[CHANGELOG](https://github.com/ticofab/scala-jev-sdk/blob/master/CHANGELOG.md).
This repo pins `0.1.0` in `project.scala`.

**Scala CLI:**

```scala
//> using dep "io.github.ticofab::scala-jev-sdk:0.1.0"
```

**Environment** (read by `JevConfig.fromEnv` and one-arg `JevClient.create`):

| Variable | Meaning | Default |
| --- | --- | --- |
| `TYPESAFE_API_KEY` | API key | required |
| `TYPESAFE_BASE_URL` | API root | `https://api.typesafe.ai` |
| `TYPESAFE_DEFAULT_MODEL` | Default model | `jev-latest` |

Keep credentials server-side. `JevConfig.toString` masks the key.

## Core workflow

Single package import:

```scala
import io.github.ticofab.jev._
```

1. Create an sttp backend (you own lifecycle and pooling).
2. `JevClient.create(backend)` or `create(backend, config)` → `Either[JevError, JevClient[F]]`.
3. Define **question values** (`val isUrgent = Question.noul(...)`).
4. `client.ask(state, q1, q2, ...)` or `client.run(JevRequest(...))`.
5. Look up answers with **the question value**: `response.answers.get(isUrgent)`.

That lookup is the central API idea: typed `Option[NoulAnswer]` (etc.), not `Map[String, Any]`.

**Minimal sync example** in this repo: [`src/main/scala/Main.scala`](../../../src/main/scala/Main.scala).

```scala
import io.github.ticofab.jev._
import sttp.client4.DefaultSyncBackend

val backend = DefaultSyncBackend()
try
  JevClient.create(backend) match
    case Left(err) => ...
    case Right(client) =>
      val isUrgent = Question.noul("is_urgent", "Does this convey urgency?")
      client.ask("Help! My payouts have been failing for 3 days.", isUrgent) match
        case Left(err) => ...
        case Right(response) =>
          response.answers.get(isUrgent).foreach(a => println(a.probability))
finally
  backend.close()
```

## Defining questions

**Factories:** `Question.noul`, `Question.choice`, `Question.score`, and
`Question.choiceOf[T]` for domain types that round-trip unchanged.

```scala
val department = Question.choice(
  "department",
  "Which team should handle this?",
  "billing"   -> "Payments, invoicing, refunds",
  "technical" -> "Bugs, outages, integrations"
)

sealed trait Team
object Team { case object Billing extends Team; case object Technical extends Team }

val dept = Question.choiceOf[Team](
  "department",
  "Which team?",
  ChoiceOption(Team.Billing, "billing", "Payments and refunds"),
  ChoiceOption(Team.Technical, "technical", "Bugs and outages")
)
```

**Varargs vs collections:** case-class forms take `Seq`; varargs factories accept splat
(`levels*`). When options or score levels come from a dataset:

```scala
val levels: Seq[Content] = labels.map(Content.text)
val frustration = Question.Score("frustration", "How frustrated?", levels)

val rows: Seq[(String, String)] = loadTaxonomy()
val department = Question.Choice("department", "Which team?", rows.map(ChoiceOption.fromPair))
```

**Pitfall:** implicit conversions from `String` → `Content` and tuple → `ChoiceOption`
apply to **one argument at a time**, not inside a `Seq`. Map collections first, as above.

**Dynamic question lists:** build `Seq[Question]` and use `client.run(JevRequest(state, questions))`
or `JevRequest.of(state, q1, q2)` for varargs.

## Reading answers

**Noul:**

```scala
response.answers.get(isUrgent).foreach { urgent =>
  urgent.probability.value   // 0.92
  urgent.probability.percent // 92.0
  urgent.isYes()             // default threshold 0.5
  urgent.isYes(0.95)
}
```

There is no `Boolean` on the answer; thresholds are explicit in application code.

**Choice:**

```scala
response.answers.get(department).foreach { team =>
  team.choice                        // selected option (or your T)
  team.confidence
  team.ranked                        // options by probability
  team.probabilityOf(Team.Billing)
  team.choiceIfConfident(0.9)        // None when too close to call
}
```

**Score:** `.score`, `.nearestLabel`. Response exposes token **usage** (e.g. `inputTokens`).

**Probability** is a dedicated type (values outside \[0, 1\] fail decoding). Construct via
`Probability.clamp` or `Probability.from`.

## State and instructions

Use **Content** for text or JSON. Strings convert implicitly where the API allows.

```scala
val consistent = Question.noul(
  "consistent_sender",
  Content.obj(
    "question" -> ujson.Str("Does identity conflict with sending domain?"),
    "checking" -> ujson.Arr("from", "display_name")
  )
)

client.ask(
  Content.obj(
    "from"         -> ujson.Str("billing@acme-support.test"),
    "display_name" -> ujson.Str("Acme Billing")
  ),
  consistent
)
```

## HTTP backends

One constructor shape: `JevClient.create(backend, config)` or with `RetryPolicy`.
Effect type `F` is whatever the backend uses; the rest of the SDK is unchanged.

| Use case | Backend | Notes |
| --- | --- | --- |
| Scripts, tests, batch | `DefaultSyncBackend` | `JevClient[Identity]` — `Identity[A]` is `A` |
| `Future` | `DefaultFutureBackend` | Needs `ExecutionContext`; no extra sttp module |
| cats-effect | `HttpClientCatsBackend` | Add sttp `"cats"` artifact; see SDK examples |
| ZIO, Monix, Pekko | respective sttp backends | Same pattern as cats |

A client is a thin value over the backend. To change config, build another client with the
same backend. Override model for one call: `JevRequest.of(state, q).withModel("jev-1.13.0")`.

**cats-effect retries:** provide `implicit val sleeper: Sleeper[IO] = Sleeper.fromFunction[IO](IO.sleep)`
even when using `RetryPolicy.none`.

Runnable examples in the SDK repo: `FutureExample`, `SyncExample`, `CatsEffectExample`,
`ErrorHandlingExample` under `examples/`.

## Configuration

```scala
val config = JevConfig("sk-...")
  .withModel("jev-1.13.0")   // default jev-latest
  .withTimeout(10.seconds)   // default 30s
  .withHeader("X-Tenant", "acme")

import sttp.model.Uri
Uri.parse("https://jev.internal.test/proxy").map(config.withBaseUrl)

JevConfig.fromEnv.flatMap(JevClient.create(backend, _, policy))
```

## Failures and retries

**Nothing throws.** Client creation, request building, and API calls surface errors as values.

- Calls return `F[Either[JevError, JevResponse]]` (sync backend: `Either` directly).
- Invalid questions are rejected **locally** as `JevError.InvalidRequest` with **all** issues listed.
- Bad config at create time: `JevError.InvalidConfig` (e.g. empty API key).

Handle common cases:

```scala
client.ask(message, isUrgent).map {
  case Right(response)                  => route(response)
  case Left(e: JevError.Validation)     => ... // 422
  case Left(e: JevError.Authentication) => ... // 401
  case Left(e: JevError.RateLimited)    => ... // 429; e.retryAfter
  case Left(e) if e.isRetryable         => ... // 529, 5xx, connection
  case Left(e)                          => ...
}
```

Default retries: three attempts after the first, exponential backoff (500 ms → 8 s cap, 20% jitter),
only on retryable failures. `Retry-After` overrides computed backoff. 422 and local `InvalidRequest`
are never retried.

```scala
JevClient.create(backend, config, RetryPolicy(maxRetries = 5))
JevClient.create(backend, config, RetryPolicy.none)
```

Alternatively use `JevError.isRetryable` and your stack (cats-retry, ZIO `Schedule`, etc.) with
`RetryPolicy.none`.

`answers.get(question)` returns `None` only when the question was not part of the request;
for questions that were sent, decoding ensures presence.

## Escape hatches

- `response.answers.raw("name")` when you do not hold the `Question` value.
- `Content.json(...)`, `Content.parse(...)` for arbitrary JSON shapes.
- `client.listModels` for account-visible models.

## Naming and imports

Prefixed: `JevClient`, `JevConfig`, `JevError`, `JevRequest`, `JevResponse`. Unprefixed:
`Question`, `Answer`, `Content`, `Probability`, `RetryPolicy`. Public API lives in
`io.github.ticofab.jev` only.

## Sources of truth

| Need | Where |
| --- | --- |
| SDK API, backends, examples | [scala-jev-sdk README](https://github.com/ticofab/scala-jev-sdk/blob/master/README.md) |
| Judgment design, primitives, cookbooks | [typesafe-ai skill](../typesafe-ai/SKILL.md), [docs index](https://docs.typesafe.ai/llms.txt) |
| HTTP API details | [docs.typesafe.ai/api.md](https://docs.typesafe.ai/api.md) |

When SDK version and docs diverge, trust installed types and the SDK README for client behavior;
use live docs for question wording and API contracts.
