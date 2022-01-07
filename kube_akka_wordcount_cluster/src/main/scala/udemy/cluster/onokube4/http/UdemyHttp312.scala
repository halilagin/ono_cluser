package udemy.cluster.onokube4.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object HighLevelApi extends App {

  implicit  val system = ActorSystem("HttpActor")
  implicit  val materilizer = ActorMaterializer()
  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route = {
    path("home") {
      get {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |well done!
            |</body>
            |</html>
            |""".stripMargin
        ))
      } ~ post{
        complete(StatusCodes.Forbidden)
      }
    }
  }

Http().bindAndHandle(simpleRoute, "localhost", 8000)
}