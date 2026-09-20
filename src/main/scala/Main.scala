import io.github.ticofab.jev._
import sttp.client4.DefaultSyncBackend

object Main:
  def main(args: Array[String]): Unit =
    // Requires TYPESAFE_API_KEY in the environment.
    val backend = DefaultSyncBackend()
    try
      JevClient.create(backend) match
        case Left(err) =>
          System.err.println(err.getMessage)
          sys.exit(1)
        case Right(client) =>
          val isUrgent = Question.noul("is_urgent", "Does this convey urgency?")
          val message = "Help! My payouts have been failing for 3 days."
          client.ask(message, isUrgent) match
            case Left(err) =>
              System.err.println(err.getMessage)
              sys.exit(1)
            case Right(response) =>
              response.answers.get(isUrgent) match
                case None =>
                  System.err.println("No answer for is_urgent")
                  sys.exit(1)
                case Some(answer) =>
                  println(s"urgency probability: ${answer.probability}")
    finally
      backend.close()
