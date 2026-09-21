import batch.AnswerColumns
import io.github.ticofab.jev._
import munit.FunSuite

class AnswerColumnsTest extends FunSuite:

  private val isUrgent = Question.noul("is_urgent", "Urgent?")
  private val dept = Question.choice(
    "department",
    "Team?",
    "billing"   -> "Payments",
    "technical" -> "Bugs"
  )
  private val readiness = Question.score(
    "sales_readiness",
    "How ready?",
    "cold",
    "warm",
    "hot"
  )

  test("extraHeaders lists flat columns and jev_error"):
    val headers = AnswerColumns.extraHeaders(Seq(isUrgent, dept, readiness))
    assertEquals(
      headers,
      Seq(
        "is_urgent_probability",
        "department_choice",
        "department_confidence",
        "sales_readiness_score",
        "sales_readiness_nearest_label",
        "jev_error"
      )
    )

  test("valuesForError pads blanks and sets jev_error"):
    val cells = AnswerColumns.valuesForError(Seq(isUrgent, dept), "rate limited")
    assertEquals(cells.size, 4)
    assertEquals(cells.init.forall(_.isEmpty), true)
    assertEquals(cells.last, "rate limited")
