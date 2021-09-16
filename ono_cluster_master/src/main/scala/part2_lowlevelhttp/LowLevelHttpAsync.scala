package part2_lowlevelhttp

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl.Sink

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
object LowLevelHttpAsync extends App {

  implicit  val system = ActorSystem("LowLevelHttp")
  implicit val materilizer = ActorMaterializer()
  import system.dispatcher



  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
        """
          |<html><body>hello from akka http! </body></html>
          |""".stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(StatusCodes.NotFound,
        entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
          """
            |<html><body>The resource not found! </body></html>
            |""".stripMargin
        )
      ))
  }

  val httpASyncConnectionHandler = Sink.foreach[IncomingConnection]{connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8000)
}
