package ono


import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import ono.util.ConfigManager



object  OnoClient extends App{
  val mainThread = Thread.currentThread
  private val conf: Config = ConfigManager.read(args)
  val masterUri = conf.getString("ono.client.masterUri")

  implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
  implicit val executionContext = system.executionContext

  //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://10.96.5.6:8080/home"))
  val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = masterUri))

  responseFuture
  .onComplete {
    case Success(res) => println(res)
    case Failure(ex)   =>   println(ex)

  }







  


}
