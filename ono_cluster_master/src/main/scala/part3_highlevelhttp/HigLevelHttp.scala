package part3_highlevelhttp

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.control.Breaks.break

object  HigLevelHttp extends App{
  val mainThread = Thread.currentThread

  implicit val system = ActorSystem("system")
  implicit  val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val routes: Route =
    path ("home"){
      get {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html><body style='color:red;'>hello!</body></html>
            |""".stripMargin
        ))
      } ~
      post{
        complete(StatusCodes.Forbidden)
      }
    } ~
    path ("api" / "item" / IntNumber / IntNumber) { (number1: Int, number2: Int) =>
      complete(HttpEntity(
        ContentTypes.`application/json`,
        s"""
          |{
          |"number1": $number1
          |"number2": $number2
          |}
          |""".stripMargin
      ))
    } ~
    pathEndOrSingleSlash {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html><body style='color:red;'>hello!</body></html>
          |""".stripMargin
      ))
    } ~
      path ("api" / "item") {
        parameter("id") { itemId:String =>
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          s"""
            |id: $itemId
            |""".stripMargin
          ))

        }
      } ~
      path ("allControl") {
        extractRequest { (request:HttpRequest) =>
          extractLog{ (log:LoggingAdapter) =>
            log.info("allcontrol is requested!")
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
              """full control!"""
            ))
          }


        }
      } ~
      ( path("merged_directives") & get & extractRequest & extractLog) { (request, log) =>
        log.info("merged directive requested")
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          s"""
             |hello merged directive!
             |""".stripMargin
        ))
      } ~
      ( (path("about") | path("aboutUs") )& get & extractRequest & extractLog) { (request, log) =>
        log.info("about path requested")
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          s"""
             |hello about!!
             |""".stripMargin
        ))
      }


  val binding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "0.0.0.0", 8080)


  println("binding!")
//  val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)
//    .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

  while (true) {
    val input: String = StdIn.readLine()
    //if (input != "stop")
      //println("server: do nothing.")
    if (input == "stop") {
      println("server: stoppping.")
      binding
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
      System.exit(0)
    }

  }

//  val binding: Future[Http.ServerBinding] =
//    Http().newServerAt("127.0.0.1", 8080).bind(routes)




//  Runtime.getRuntime.addShutdownHook(new Thread() {override def run = {
//    println("inside addShutDownHook handler")
//    mainThread.join
//
//
//  }})

//  CoordinatedShutdown(system).addCancellableJvmShutdownHook {
//    val onceAllConnectionsTerminated: Future[Http.HttpTerminated] =
//      Await.result(binding, 10.seconds)
//        .terminate(hardDeadline = 3.seconds)
//
//    // once all connections are terminated,
//    // - you can invoke coordinated shutdown to tear down the rest of the system:
//    onceAllConnectionsTerminated.flatMap { _ =>
//      system.terminate()
//    }
//
//  }

//
//  CoordinatedShutdown(system).addTask(
//    CoordinatedShutdown.PhaseServiceUnbind, "http_shutdown") { () =>
//
//    binding.flatMap(_.terminate(hardDeadline = 10.seconds)).map { _ =>
//      Done
//    }
//  }
//

}
