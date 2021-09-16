package part2_lowlevelhttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

object LowLevelHttpSync extends App {

  implicit  val system = ActorSystem("LowLevelHttp")
  implicit val materilizer = ActorMaterializer()



  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
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

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection]{connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  //Http().bind("localhost", 8000).runWith(httpSyncConnectionHandler)

  //Http().bindAndHandleSync(requestHandler, "localhost", 8000)
}
