package io.github.aboisvert.jevvy.batch

import io.github.aboisvert.jevvy.config.LoadedBatchConfig
import io.github.aboisvert.jevvy.csv.CsvIO
import io.github.ticofab.jev._
import sttp.client4.DefaultSyncBackend

import gears.async.*
import gears.async.default.given

import scala.collection.mutable

final case class BatchResult(outputPath: String, total: Int, failures: Int)

object BatchRunner:

  def run(
      config: LoadedBatchConfig,
      inputPath: String,
      outputPath: String
  ): Either[String, BatchResult] =
    val backend = DefaultSyncBackend()
    val clientE =
      try JevClient.create(backend, config.jevConfig).left.map(_.getMessage)
      catch
        case e: Exception =>
          backend.close()
          Left(e.getMessage)

    clientE.flatMap { client =>
      try
        CsvIO.readRows(inputPath) { (headers, rows) =>
          runWith(config, headers, rows, outputPath, req => client.run(req))
        }.flatMap(identity)
      finally backend.close()
    }

  private[jevvy] def runWith(
      config: LoadedBatchConfig,
      headers: Seq[String],
      rows: Iterator[Map[String, String]],
      outputPath: String,
      runRequest: JevRequest => Either[JevError, JevResponse]
  ): Either[String, BatchResult] =
    val outHeaders = headers ++ AnswerColumns.extraHeaders(config.questions)
    Async.blocking:
      CsvIO.writeRows(outputPath, outHeaders) { writeRow =>
        processRowsOrdered(config, headers, rows, runRequest, writeRow)
          .copy(outputPath = outputPath)
      }

  private def processRowsOrdered(
      config: LoadedBatchConfig,
      headers: Seq[String],
      rowIter: Iterator[Map[String, String]],
      runRequest: JevRequest => Either[JevError, JevResponse],
      writeRow: Seq[String] => Unit
  )(using Async): BatchResult =
    Async.group:
      val window = mutable.Queue.empty[Future[Seq[String]]]
      var rowIdx = 0
      var failures = 0
      var total = 0

      def logProgress(): Unit =
        if total % 10 == 0 then System.err.println(s"processed $total")

      def rowFields(idx: Int, row: Map[String, String]): Seq[String] =
        try
          if config.delayMs > 0 && idx > 0 then AsyncOperations.sleep(config.delayMs)
          val inputFields = CsvIO.rowValuesInOrder(headers, row)
          val state = CsvIO.rowToState(row)
          val request = JevRequest(state, config.questions)
          val extraFields =
            runRequest(request) match
              case Left(err) =>
                AnswerColumns.valuesForError(config.questions, err.getMessage)
              case Right(response) =>
                AnswerColumns.valuesForSuccess(response, config.questions) match
                  case Left(msg)    => AnswerColumns.valuesForError(config.questions, msg)
                  case Right(fields) => fields :+ ""
          inputFields ++ extraFields
        catch
          case e: Exception =>
            CsvIO.rowValuesInOrder(headers, row) ++
              AnswerColumns.valuesForError(config.questions, e.getMessage)

      def enqueueNext(): Unit =
        if rowIter.hasNext then
          val idx = rowIdx
          rowIdx += 1
          val row = rowIter.next()
          window.enqueue(Future(rowFields(idx, row)))

      // enqueue initial rows (at most `config.concurrency`)
      while window.size < config.concurrency && rowIter.hasNext do enqueueNext()

      while window.nonEmpty do
        // dequeue a row and write its fields
        val rowFields = window.dequeue().await
        writeRow(rowFields)
        if rowFields.lastOption.exists(_.nonEmpty) then failures += 1
        total += 1
        logProgress()
        enqueueNext()

      BatchResult("", total, failures)
