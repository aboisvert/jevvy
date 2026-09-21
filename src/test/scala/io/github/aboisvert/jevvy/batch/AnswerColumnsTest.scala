package io.github.aboisvert.jevvy.batch

import io.github.ticofab.jev.*
import io.github.ticofab.jev.JevResponseFixtures
import munit.FunSuite

class AnswerColumnsTest extends FunSuite:

  private val isUrgent = Question.noul("is_urgent", "Urgent?")
  private val dept     = Question.choice(
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
    val fields = AnswerColumns.valuesForError(Seq(isUrgent, dept), "rate limited")
    assertEquals(fields.size, 4)
    assertEquals(fields.init.forall(_.isEmpty), true)
    assertEquals(fields.last, "rate limited")

  test("valuesForSuccess extracts noul probability"):
    val body     = JevResponseFixtures.bodyForNoul("is_urgent", 0.75)
    val response = JevResponseFixtures.parse(body, Seq(isUrgent)).getOrElse(fail("parse failed"))
    assertEquals(
      AnswerColumns.valuesForSuccess(response, Seq(isUrgent)),
      Right(Seq("0.7500"))
    )

  test("valuesForSuccess extracts choice and confidence"):
    val body = JevResponseFixtures.bodyForChoice(
      "department",
      "billing",
      "billing"   -> 0.8,
      "technical" -> 0.2
    )
    val response = JevResponseFixtures.parse(body, Seq(dept)).getOrElse(fail("parse failed"))
    assertEquals(
      AnswerColumns.valuesForSuccess(response, Seq(dept)),
      Right(Seq("billing", "0.9000"))
    )

  test("valuesForSuccess extracts score and nearest label"):
    val body =
      JevResponseFixtures.bodyForScore("sales_readiness", 1.6, 0 -> 0.1, 1 -> 0.3, 2 -> 0.6)
    val response = JevResponseFixtures.parse(body, Seq(readiness)).getOrElse(fail("parse failed"))
    val fields   =
      AnswerColumns.valuesForSuccess(response, Seq(readiness)).getOrElse(fail("expected success"))
    assertEquals(fields.head, "1.6000")
    assertEquals(fields(1), "hot")

  test("valuesForSuccess fails when answer missing"):
    val body     = JevResponseFixtures.bodyForNoul("is_urgent", 0.5)
    val response = JevResponseFixtures.parse(body, Seq(isUrgent)).getOrElse(fail("parse failed"))
    assert(AnswerColumns.valuesForSuccess(response, Seq(isUrgent, dept)).isLeft)
