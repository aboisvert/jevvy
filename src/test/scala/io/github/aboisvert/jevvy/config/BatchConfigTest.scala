package io.github.aboisvert.jevvy.config

import io.github.ticofab.jev.{JevConfig, Question}
import munit.FunSuite

import scala.concurrent.duration.*

class BatchConfigTest extends FunSuite:

  private val testBaseConfig = Right(JevConfig("test-key"))

  test("loads choice question from YAML"):
    val yaml =
      """|concurrency: 3
         |questions:
         |  - name: department
         |    type: choice
         |    question: Which team?
         |    options:
         |      - id: billing
         |        description: Payments
         |      - id: technical
         |        description: Bugs
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assertEquals(file.concurrency, Some(3))
    val questions = BatchConfig.buildQuestions(file).getOrElse(fail("build failed"))
    assertEquals(questions.size, 1)
    questions.head match
      case _: Question.Choice[?] => ()
      case other                 => fail(s"expected choice, got $other")

  test("loads noul question from YAML"):
    val yaml =
      """|questions:
         |  - name: is_spam
         |    type: noul
         |    question: Is this spam?
         |""".stripMargin
    val file      = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    val questions = BatchConfig.buildQuestions(file).getOrElse(fail("build failed"))
    questions.head match
      case _: Question.Noul => ()
      case other            => fail(s"expected noul, got $other")

  test("loads valid score question from YAML"):
    val yaml =
      """|questions:
         |  - name: readiness
         |    type: score
         |    question: How ready?
         |    levels:
         |      - label: cold
         |        description: Low
         |      - label: hot
         |        description: High
         |""".stripMargin
    val file      = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    val questions = BatchConfig.buildQuestions(file).getOrElse(fail("build failed"))
    questions.head match
      case _: Question.Score => ()
      case other             => fail(s"expected score, got $other")

  test("rejects empty questions list"):
    val yaml = "questions: []"
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assert(BatchConfig.buildQuestions(file).isLeft)

  test("rejects score with one level"):
    val yaml =
      """|questions:
         |  - name: readiness
         |    type: score
         |    question: How ready?
         |    levels:
         |      - label: cold
         |        description: Low
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assert(BatchConfig.buildQuestions(file).isLeft)

  test("rejects choice with empty options"):
    val yaml =
      """|questions:
         |  - name: department
         |    type: choice
         |    question: Which team?
         |    options: []
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assert(BatchConfig.buildQuestions(file).isLeft)

  test("rejects unknown question type"):
    val yaml =
      """|questions:
         |  - name: x
         |    type: magic
         |    question: ???
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assert(BatchConfig.buildQuestions(file).isLeft)

  test("loads bundled support-triage example"):
    val file =
      BatchConfig.loadFromFile("examples/support-triage.yaml").getOrElse(fail("yaml parse failed"))
    assertEquals(file.questions.head.name, "department")
    assertEquals(file.questions.head.`type`, "choice")

  test("resolve applies model timeout and concurrency overrides"):
    val yaml =
      """|model: yaml-model
         |timeout_seconds: 42
         |concurrency: 4
         |delay_ms: 100
         |questions:
         |  - name: is_spam
         |    type: noul
         |    question: Spam?
         |""".stripMargin
    val file   = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    val loaded =
      BatchConfig
        .resolve(file, Some(2), Some("cli-model"), testBaseConfig)
        .getOrElse(fail("resolve failed"))
    assertEquals(loaded.concurrency, 2)
    assertEquals(loaded.delayMs, 100L)
    assertEquals(loaded.jevConfig.model, "cli-model")
    assertEquals(loaded.jevConfig.timeout, 42.seconds)

  test("resolve rejects concurrency below 1"):
    val yaml =
      """|questions:
         |  - name: is_spam
         |    type: noul
         |    question: Spam?
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assertEquals(
      BatchConfig.resolve(file, Some(0), None, testBaseConfig),
      Left("concurrency must be at least 1")
    )

  test("resolve rejects negative delay_ms"):
    val yaml =
      """|delay_ms: -1
         |questions:
         |  - name: is_spam
         |    type: noul
         |    question: Spam?
         |""".stripMargin
    val file = BatchConfig.loadFromString(yaml).getOrElse(fail("yaml parse failed"))
    assertEquals(
      BatchConfig.resolve(file, None, None, testBaseConfig),
      Left("delay_ms must be non-negative")
    )
