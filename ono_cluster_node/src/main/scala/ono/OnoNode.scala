package ono


import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

object  OnoNode extends App{
  val mainThread = Thread.currentThread
  ConfigFactory.invalidateCaches()
  //call ConfigFactory.load() for dynamic load of application.conf with -Dconfig.file=
  var conf:Config  = ConfigFactory.load();

  def overrideConfigWithCmdArgs(): Unit = {
    args.filter(_.contains("conf_from")==false).foreach { c =>
      val myConfig = ConfigFactory.parseString(c)
      println("changing conf", c)
      conf = myConfig.withFallback(conf)

    }
  }

  // setup config with applicaiton.conf or with command line arguments
  args.filter(x => x.contains("conf_from=")).headOption match {
    case Some(arg) => arg.split("=")(1) match {
      case "cmd" => overrideConfigWithCmdArgs()
      case _ => //use default application.conf
    }
    case None => ; //"no command arg passed"
  }
  // conf variable is set now.

  implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
  implicit val executionContext = system.executionContext
  val masterUri = conf.getString("ono.cluster.node.masterUri")
  println("masterUri", masterUri)
  //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://10.96.5.6:8080/home"))
  val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = masterUri))

  responseFuture
  .onComplete {
    case Success(res) => println(res)
    case Failure(ex)   =>   println(ex)

  }







  


}
