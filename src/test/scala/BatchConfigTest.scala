import config.BatchConfig
import io.github.ticofab.jev.Question
import munit.FunSuite

class BatchConfigTest extends FunSuite:

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

  test("loads bundled support-triage example"):
    val file = BatchConfig.loadFromFile("examples/support-triage.yaml").getOrElse(fail("yaml parse failed"))
    assertEquals(file.questions.head.name, "department")
    assertEquals(file.questions.head.`type`, "choice")
