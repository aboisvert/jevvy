package io.github.aboisvert.jevvy.batch

import io.github.aboisvert.jevvy.config.LoadedBatchConfig
import io.github.aboisvert.jevvy.csv.{CsvIO, CsvTable}
import io.github.ticofab.jev._
import sttp.client4.DefaultFutureBackend

import java.util.concurrent.Semaphore
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

final case class BatchResult(outputPath: String, total: Int, failures: Int)

object BatchRunner:

  def run(
      config: LoadedBatchConfig,
      inputPath: String,
      outputPath: String
  ): Either[String, BatchResult] =
    given ExecutionContext = ExecutionContext.global

    for
      table <- CsvIO.read(inputPath)
      backend = DefaultFutureBackend()
      client <-
        try JevClient.create(backend, config.jevConfig).left.map(_.getMessage)
        catch
          case e: Exception =>
            backend.close()
            Left(e.getMessage)
      result <-
        try runWith(config, table, outputPath, req => client.run(req))
        finally backend.close()
    yield result

  private[jevvy] def runWith(
      config: LoadedBatchConfig,
      table: CsvTable,
      outputPath: String,
      runRequest: JevRequest => Future[Either[JevError, JevResponse]]
  )(using ExecutionContext): Either[String, BatchResult] =
    val outHeaders = table.headers ++ AnswerColumns.extraHeaders(config.questions)
    val total = table.rows.size
    val sem = Semaphore(config.concurrency)
    var completed = 0

    def logProgress(): Unit =
      completed += 1
      if completed == total || completed % 10 == 0 then
        System.err.println(s"processed $completed/$total")

    val futures = table.rows.zipWithIndex.map { (row, idx) =>
      Future {
        sem.acquire()
        try
          if config.delayMs > 0 && idx > 0 then Thread.sleep(config.delayMs)
          val inputCells = CsvIO.rowValuesInOrder(table.headers, row)
          val state = CsvIO.rowToState(row)
          val request = JevRequest(state, config.questions)
          val extraCells =
            Await.result(runRequest(request), Duration.Inf) match
              case Left(err) =>
                AnswerColumns.valuesForError(config.questions, err.getMessage)
              case Right(response) =>
                AnswerColumns.valuesForSuccess(response, config.questions) match
                  case Left(msg)    => AnswerColumns.valuesForError(config.questions, msg)
                  case Right(cells) => cells :+ ""
          inputCells ++ extraCells
        finally sem.release()
      }.andThen { _ =>
        synchronized(logProgress())
      }
    }

    val outRows = Await.result(Future.sequence(futures), Duration.Inf)
    val failures = outRows.count(_.lastOption.exists(_.nonEmpty))

    CsvIO.write(outputPath, outHeaders, outRows).map(_ =>
      BatchResult(outputPath, total, failures)
    )
