import batch.BatchRunner
import config.BatchConfig
import mainargs.{arg, main, ParserForMethods}

object Main:

  def main(args: Array[String]): Unit =
    val cliArgs = args.toList match
      case "batch" :: rest => rest.toArray
      case _               => args

    if cliArgs.isEmpty then
      System.err.println("Usage: batch --config PATH --input PATH --output PATH [--concurrency N] [--model NAME]")
      System.err.println("Example:")
      System.err.println("  scala-cli run . -- --config examples/support-triage.yaml --input examples/support-triage.csv --output out.csv")
      sys.exit(1)
    else ParserForMethods(this).runOrExit(cliArgs.toSeq)

  @main(
    doc = """Run Jev on each CSV row (row → JSON state) using questions from a YAML config.
            |Appends flat result columns plus jev_error. Exit 1 if any row fails."""
  )
  def batch(
      @arg(doc = "Path to YAML config") config: String,
      @arg(doc = "Input CSV path") input: String,
      @arg(doc = "Output CSV path") output: String,
      @arg(doc = "Override YAML concurrency") concurrency: Option[Int] = None,
      @arg(doc = "Override YAML model") model: Option[String] = None
  ): Unit =
    val result =
      for
        file <- BatchConfig.loadFromFile(config)
        loaded <- BatchConfig.resolve(file, concurrency, model)
        batchResult <- BatchRunner.run(loaded, input, output)
      yield batchResult

    result match
      case Left(err) =>
        System.err.println(err)
        sys.exit(1)
      case Right(batchResult) =>
        System.err.println(
          s"Wrote ${batchResult.outputPath} (${batchResult.total} rows, ${batchResult.failures} failed)"
        )
        if batchResult.failures > 0 then sys.exit(1)
