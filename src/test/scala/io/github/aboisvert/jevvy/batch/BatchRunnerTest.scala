package io.github.aboisvert.jevvy.batch

import io.github.aboisvert.jevvy.config.LoadedBatchConfig
import io.github.aboisvert.jevvy.csv.CsvIO
import io.github.ticofab.jev.*
import io.github.ticofab.jev.JevResponseFixtures
import munit.FunSuite

import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}

class BatchRunnerTest extends FunSuite:

  private given ExecutionContext = ExecutionContext.global

  private val isUrgent = Question.noul("is_urgent", "Urgent?")

  private def loadedConfig: LoadedBatchConfig =
    LoadedBatchConfig(JevConfig("test-key"), concurrency = 1, delayMs = 0L, questions = Seq(isUrgent))

  test("runWith writes success rows with empty jev_error"):
    val outPath = Files.createTempFile("jevvy-out", ".csv").toString
    val inputPath = Files.createTempFile("jevvy-in", ".csv").toString
    try
      assert(
        CsvIO
          .write(inputPath, Seq("message"), Seq(Seq("help")))
          .isRight
      )
      val table = CsvIO.read(inputPath).getOrElse(fail("read input failed"))
      val body = JevResponseFixtures.bodyForNoul("is_urgent", 0.9)
      val response = JevResponseFixtures.parse(body, Seq(isUrgent)).getOrElse(fail("parse failed"))
      val result =
        BatchRunner.runWith(
          loadedConfig,
          table.headers,
          table.rows.iterator,
          outPath,
          _ => Future.successful(Right(response))
        ).getOrElse(fail("runWith failed"))
      assertEquals(result.total, 1)
      assertEquals(result.failures, 0)
      val written = CsvIO.read(outPath).getOrElse(fail("read output failed"))
      assertEquals(
        written.headers,
        Seq("message") ++ AnswerColumns.extraHeaders(Seq(isUrgent))
      )
      assertEquals(written.rows.head.get("jev_error"), Some(""))
      assertEquals(written.rows.head.get("is_urgent_probability"), Some("0.9000"))
    finally
      Files.deleteIfExists(java.nio.file.Path.of(outPath))
      Files.deleteIfExists(java.nio.file.Path.of(inputPath))

  test("runWith records API errors in jev_error column"):
    val outPath = Files.createTempFile("jevvy-out-err", ".csv").toString
    val inputPath = Files.createTempFile("jevvy-in-err", ".csv").toString
    try
      assert(CsvIO.write(inputPath, Seq("message"), Seq(Seq("x"))).isRight)
      val result =
        BatchRunner
          .runWith(
            loadedConfig,
            Seq("message"),
            Iterator(Map("message" -> "x")),
            outPath,
            _ => Future.successful(Left(JevError.RateLimited(None, "rate limited")))
          )
          .getOrElse(fail("runWith failed"))
      assertEquals(result.failures, 1)
      val written = CsvIO.read(outPath).getOrElse(fail("read output failed"))
      assert(written.rows.head.get("jev_error").exists(_.contains("rate limited")))
    finally
      Files.deleteIfExists(java.nio.file.Path.of(outPath))
      Files.deleteIfExists(java.nio.file.Path.of(inputPath))

  test("runWith preserves row order under concurrency"):
    val outPath = Files.createTempFile("jevvy-out-order", ".csv").toString
    val inputPath = Files.createTempFile("jevvy-in-order", ".csv").toString
    val config = loadedConfig.copy(concurrency = 2)
    try
      assert(
        CsvIO
          .write(
            inputPath,
            Seq("message"),
            Seq(Seq("slow"), Seq("b"), Seq("c"))
          )
          .isRight
      )
      val table = CsvIO.read(inputPath).getOrElse(fail("read input failed"))
      val body = JevResponseFixtures.bodyForNoul("is_urgent", 0.5)
      val response = JevResponseFixtures.parse(body, Seq(isUrgent)).getOrElse(fail("parse failed"))
      val result =
        BatchRunner
          .runWith(
            config,
            table.headers,
            table.rows.iterator,
            outPath,
            req =>
              val slow = req.state.asString.contains("slow")
              Future {
                if slow then Thread.sleep(150)
                Right(response)
              }
          )
          .getOrElse(fail("runWith failed"))
      assertEquals(result.total, 3)
      val written = CsvIO.read(outPath).getOrElse(fail("read output failed"))
      assertEquals(
        written.rows.map(_("message")),
        Seq("slow", "b", "c")
      )
    finally
      Files.deleteIfExists(java.nio.file.Path.of(outPath))
      Files.deleteIfExists(java.nio.file.Path.of(inputPath))
