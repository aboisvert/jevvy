package io.github.aboisvert.jevvy.config

import io.github.ticofab.jev._
import org.virtuslab.yaml.*
import org.virtuslab.yaml.YamlCodec.derived

import scala.concurrent.duration.*

final case class ChoiceOptionYaml(id: String, description: String) derives YamlCodec

final case class ScoreLevelYaml(label: String, description: String) derives YamlCodec

final case class QuestionYaml(
    name: String,
    `type`: String,
    question: String,
    options: Option[List[ChoiceOptionYaml]] = None,
    levels: Option[List[ScoreLevelYaml]] = None
) derives YamlCodec

final case class BatchConfigFile(
    model: Option[String] = None,
    timeout_seconds: Option[Int] = None,
    concurrency: Option[Int] = None,
    delay_ms: Option[Long] = None,
    questions: List[QuestionYaml]
) derives YamlCodec

/** Resolved settings and SDK question values ready for batch execution. */
final case class LoadedBatchConfig(
    jevConfig: JevConfig,
    concurrency: Int,
    delayMs: Long,
    questions: Seq[Question]
)

object BatchConfig:

  def loadFromFile(path: String): Either[String, BatchConfigFile] =
    val source = scala.io.Source.fromFile(path)
    try loadFromString(source.mkString)
    catch case e: Exception => Left(s"cannot read config $path: ${e.getMessage}")
    finally source.close()

  def loadFromString(yamlText: String): Either[String, BatchConfigFile] =
    yamlText.as[BatchConfigFile].left.map(err => s"invalid YAML config: $err")

  def buildQuestions(file: BatchConfigFile): Either[String, Seq[Question]] =
    if file.questions.isEmpty then Left("config must define at least one question")
    else
      file.questions
        .foldLeft(Right(Nil): Either[String, List[Question]]) { (acc, q) =>
          acc.flatMap { qs =>
            buildQuestion(q).map(_ :: qs)
          }
        }
        .map(_.reverse)

  private def validateQuestion(question: Question): Either[String, Question] =
    JevRequest(Content.text(""), Seq(question)).validate.left.map(_.getMessage).map(_ => question)

  def buildQuestion(q: QuestionYaml): Either[String, Question] =
    q.`type`.trim.toLowerCase match
      case "noul" =>
        Right(Question.noul(q.name, q.question))
      case "choice" =>
        q.options match
          case None | Some(Nil) =>
            Left(s"choice question '${q.name}' requires non-empty options")
          case Some(opts) =>
            val built = opts.map(o => ChoiceOption(o.id, o.id, Some(Content.text(o.description))))
            validateQuestion(Question.Choice(q.name, q.question, built))
      case "score" =>
        q.levels match
          case None =>
            Left(s"score question '${q.name}' requires at least 2 levels")
          case Some(levels) if levels.sizeIs < 2 =>
            Left(s"score question '${q.name}' requires at least 2 levels")
          case Some(levels) =>
            val contentLevels = levels.map(l => Content.text(s"${l.label}: ${l.description}"))
            validateQuestion(Question.Score(q.name, q.question, contentLevels))
      case other =>
        Left(s"question '${q.name}' has unknown type '$other' (expected noul, choice, or score)")

  def resolve(
      file: BatchConfigFile,
      cliConcurrency: Option[Int],
      cliModel: Option[String]
  ): Either[String, LoadedBatchConfig] =
    resolve(file, cliConcurrency, cliModel, JevConfig.fromEnv.left.map(_.getMessage))

  private[jevvy] def resolve(
      file: BatchConfigFile,
      cliConcurrency: Option[Int],
      cliModel: Option[String],
      baseConfig: Either[String, JevConfig]
  ): Either[String, LoadedBatchConfig] =
    for
      questions <- buildQuestions(file)
      config <- baseConfig
      jevConfig = applyConfigOverrides(config, file, cliModel)
      concurrency = cliConcurrency.orElse(file.concurrency).getOrElse(1)
      _ <-
        if concurrency < 1 then Left("concurrency must be at least 1")
        else Right(())
      delayMs = file.delay_ms.getOrElse(0L)
      _ <-
        if delayMs < 0 then Left("delay_ms must be non-negative")
        else Right(())
    yield LoadedBatchConfig(jevConfig, concurrency, delayMs, questions)

  private def applyConfigOverrides(
      base: JevConfig,
      file: BatchConfigFile,
      cliModel: Option[String]
  ): JevConfig =
    var c = base
    file.model.foreach(m => c = c.withModel(m))
    file.timeout_seconds.foreach(s => c = c.withTimeout(s.seconds))
    cliModel.foreach(m => c = c.withModel(m))
    c
