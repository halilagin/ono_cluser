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


  val conf:Config  = ConfigFactory.load("application.conf");
  conf.getObject("ono").for{ (k,v) => 
    println(k,v)
  }



  args.map(println)


  val mainThread = Thread.currentThread

  //implicit val system = ActorSystem("system")
  implicit val system = ActorSystem(Behaviors.empty, "SingleRequest")
  implicit val executionContext = system.executionContext


  val java_env = System.getenv("ONO_MASTER_URI")
  println("java_env", java_env)
  val ono_master_uri:String =  sys.env.getOrElse("ONO_MASTER_URI", "http://10.96.5.6:8080/home")
  println(s"ONO_MASTER_URI:", ono_master_uri)

  //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://10.96.5.6:8080/home"))
  val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = ono_master_uri))

  responseFuture
  .onComplete {
    case Success(res) => println(res)
    case Failure(ex)   =>   println(ex)

  }







  


}
