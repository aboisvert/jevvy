package io.github.aboisvert.jevvy

import io.github.aboisvert.jevvy.batch.BatchRunner
import io.github.aboisvert.jevvy.cli.BatchPaths
import io.github.aboisvert.jevvy.config.BatchConfig
import io.github.ticofab.jev.JevConfig
import mainargs.{arg, main, ParserForMethods}

object Main:

  def main(args: Array[String]): Unit =
    if args.isEmpty then
      System.err.println(
        "Usage: jevvy --input PATH [--config PATH] [--output PATH] [--concurrency N] [--model NAME] [--max-retries N]"
      )
      System.err.println("Example:")
      System.err.println("  scala-cli run . -- --input examples/support-triage.csv")
      sys.exit(1)
    else ParserForMethods(this).runOrExit(args.toSeq)

  @main(
    name = "jevvy",
    doc = """Run Jev on each CSV row (row → JSON state) using questions from a YAML config.
            |Config defaults to {input stem}.yaml; output to {input stem}-out.csv.
            |Appends flat result columns plus jev_error. Exit 1 if any row fails."""
  )
  def run(
    @arg(doc = "Input CSV path") input: String,
    @arg(doc = "Path to YAML config (default: input stem + .yaml)") config: Option[String] = None,
    @arg(doc = "Output CSV path (default: input stem + -out.csv)") output: Option[String] = None,
    @arg(doc = "Override YAML concurrency") concurrency: Option[Int] = None,
    @arg(doc = "Override YAML model") model: Option[String] = None,
    @arg(doc = "Override YAML max_retries (0 disables SDK retries)") maxRetries: Option[Int] = None
  ): Unit =
    // Fail before path/config work so missing TYPESAFE_API_KEY is reported immediately.
    JevConfig.fromEnv.left.foreach { err =>
      System.err.println(err.getMessage)
      sys.exit(1)
    }
    val (configPath, inputPath, outputPath) = BatchPaths.resolve(input, config, output)
    val result                              =
      for
        file        <- BatchConfig.loadFromFile(configPath)
        loaded      <- BatchConfig.resolve(file, concurrency, model, maxRetries)
        batchResult <- BatchRunner.run(loaded, inputPath, outputPath)
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
