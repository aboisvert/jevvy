package batch

import io.github.ticofab.jev._

object AnswerColumns:

  val ErrorColumn: String = "jev_error"

  def extraHeaders(questions: Seq[Question]): Seq[String] =
    questions.flatMap(headerNamesFor) :+ ErrorColumn

  def headerNamesFor(question: Question): Seq[String] =
    question match
      case q: Question.Noul           => Seq(s"${q.name}_probability")
      case q: Question.Choice[?]      => Seq(s"${q.name}_choice", s"${q.name}_confidence")
      case q: Question.Score          => Seq(s"${q.name}_score", s"${q.name}_nearest_label")

  def valuesForSuccess(response: JevResponse, questions: Seq[Question]): Either[String, Seq[String]] =
    questions.foldLeft(Right(Seq.empty[String]): Either[String, Seq[String]]) { (acc, q) =>
      acc.flatMap(cells => cellsForQuestion(response, q).map(cells ++ _))
    }

  private def cellsForQuestion(response: JevResponse, q: Question): Either[String, Seq[String]] =
    q match
      case n: Question.Noul =>
        response.answers.get(n).toRight(missing(n.name)).map { a =>
          Seq(f"${a.probability.value}%.4f")
        }
      case c: Question.Choice[?] =>
        response.answers.get(c).toRight(missing(c.name)).map { a =>
          Seq(a.key, f"${a.confidence.value}%.4f")
        }
      case s: Question.Score =>
        response.answers.get(s).toRight(missing(s.name)).map { a =>
          Seq(f"${a.score}%.4f", a.nearestLabel)
        }

  def valuesForError(questions: Seq[Question], message: String): Seq[String] =
    val blank = questions.flatMap(q => headerNamesFor(q).map(_ => ""))
    blank :+ message

  private def missing(name: String): String =
    s"missing answer for question '$name' in response"
