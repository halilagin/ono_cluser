package udemy.cluster.onokube4.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object LowLevelApi extends App {

  implicit  val system = ActorSystem("HttpActor")
  implicit  val materilizer = ActorMaterializer()
  import system.dispatcher

  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accept incoming connection from ${connection.remoteAddress}")
  }

//  val serverSource = Http().bind("localhost", 8000)
//  val serverBindingFuture = serverSource.to(connectionSink).run()
//  serverBindingFuture.onComplete {
//    case Success(binding) =>
//      println("Server binding successful")
//      binding.terminate(2 seconds)
//    case Failure(ex) =>
//      println(s"Server bindgin failed: ${ex}")
//  }

  val requestHandler: HttpRequest => HttpResponse =  {
    case HttpRequest (HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello akka http!
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Content Not found!
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }

  var httpSyncConnectionHandler = Sink.foreach[IncomingConnection] {connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  //Http().bind("localhost", 8000).runWith(httpSyncConnectionHandler)


  val asyncRequestHandler: HttpRequest => Future[HttpResponse] =  {
    case HttpRequest (HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello akka http!
            |</body>
            |</html>
            |""".stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Content Not found!
            |</body>
            |</html>
            |""".stripMargin
        )
      ))
  }

  var httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] {connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  Http().bind("localhost", 8000).runWith(httpAsyncConnectionHandler)
}