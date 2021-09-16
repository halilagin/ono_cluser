package part2_lowlevelhttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future

object LowLevelHttpStream extends App {

  implicit  val system = ActorSystem("LowLevelHttp")
  implicit val materilizer = ActorMaterializer()
  import system.dispatcher



  val streamRequestHandler: Flow[HttpRequest, HttpResponse,_] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      HttpResponse(StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
        """
          |<html><body>hello from akka http! </body></html>
          |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(StatusCodes.NotFound,
        entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
          """
            |<html><body>The resource not found! </body></html>
            |""".stripMargin
        )
      )
  }

  Http().bind( "localhost", 8000).runForeach { connection =>
    connection.handleWith(streamRequestHandler)
  }

  //Http().bindAndHandle(streamRequestHandler, "localhost", 8000)
}
