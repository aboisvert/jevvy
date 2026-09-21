package io.github.aboisvert.jevvy.batch

import io.github.aboisvert.jevvy.config.LoadedBatchConfig
import io.github.aboisvert.jevvy.csv.CsvIO
import io.github.ticofab.jev._
import sttp.client4.DefaultFutureBackend

import java.util.concurrent.Semaphore
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

final case class BatchResult(outputPath: String, total: Int, failures: Int)

object BatchRunner:

  def run(
      config: LoadedBatchConfig,
      inputPath: String,
      outputPath: String
  ): Either[String, BatchResult] =
    given ExecutionContext = ExecutionContext.global

    val backend = DefaultFutureBackend()
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
      runRequest: JevRequest => Future[Either[JevError, JevResponse]]
  )(using ExecutionContext): Either[String, BatchResult] =
    val outHeaders = headers ++ AnswerColumns.extraHeaders(config.questions)
    CsvIO.writeRows(outputPath, outHeaders) { writeRow =>
      processRowsOrdered(config, headers, rows, runRequest, writeRow)
        .copy(outputPath = outputPath)
    }

  private def processRowsOrdered(
      config: LoadedBatchConfig,
      headers: Seq[String],
      rowIter: Iterator[Map[String, String]],
      runRequest: JevRequest => Future[Either[JevError, JevResponse]],
      writeRow: Seq[String] => Unit
  )(using ExecutionContext): BatchResult =
    val sem = Semaphore(config.concurrency)
    val pending = mutable.Map.empty[Int, Seq[String]]
    val lock = new AnyRef
    var submitIdx = 0
    var nextWrite = 0
    var inFlight = 0
    var failures = 0
    var progress = 0
    var inputExhausted = false
    val done = Promise[BatchResult]()

    def logProgress(): Unit =
      if progress % 10 == 0 then System.err.println(s"processed $progress")

    def tryFlush(): Unit =
      while pending.contains(nextWrite) do
        val cells = pending.remove(nextWrite).get
        writeRow(cells)
        if cells.lastOption.exists(_.nonEmpty) then failures += 1
        nextWrite += 1
        progress += 1
        logProgress()

    def maybeFinish(): Unit =
      if inputExhausted && inFlight == 0 && nextWrite == submitIdx then
        done.success(BatchResult("", submitIdx, failures))

    def rowCells(row: Map[String, String], idx: Int): Seq[String] =
      sem.acquire()
      try
        if config.delayMs > 0 && idx > 0 then Thread.sleep(config.delayMs)
        val inputCells = CsvIO.rowValuesInOrder(headers, row)
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

    def runOne(idx: Int, row: Map[String, String]): Unit =
      val future = Future {
        rowCells(row, idx)
      }
      future.foreach { outCells =>
        lock.synchronized:
          pending(idx) = outCells
          inFlight -= 1
          tryFlush()
          pumpSubmit()
          maybeFinish()
      }
      future.failed.foreach { err =>
        lock.synchronized:
          pending(idx) =
            CsvIO.rowValuesInOrder(headers, row) ++
              AnswerColumns.valuesForError(config.questions, err.getMessage)
          inFlight -= 1
          tryFlush()
          pumpSubmit()
          maybeFinish()
      }

    def pumpSubmit(): Unit =
      var batch = List.empty[(Int, Map[String, String])]
      while inFlight + batch.size < config.concurrency && rowIter.hasNext do
        val idx = submitIdx
        submitIdx += 1
        batch = (idx, rowIter.next()) :: batch
      if !rowIter.hasNext then inputExhausted = true
      inFlight += batch.size
      batch.reverse.foreach { (idx, row) => runOne(idx, row) }

    lock.synchronized:
      pumpSubmit()
      maybeFinish()

    Await.result(done.future, Duration.Inf)
