package ono


import java.util.Date

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}
import com.typesafe.config.Config
import ono.util.ConfigManager

import scala.concurrent.duration._
import scala.language.postfixOps


object ExternalService {
  def run(): Unit ={
    implicit  val  system = ActorSystem("externalService")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    //implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
    println("dispatcher", dispatcher.id)
    case class PagerEvent(application:String, description:String, date:Date)

    val eventSource = Source(List(
      PagerEvent("app1", "desc1", new Date),
      PagerEvent("app2", "desc2", new Date),
      PagerEvent("app3", "desc3", new Date)
    ))

    class PagerActor extends Actor with ActorLogging {
      private val engineers = List("eng1", "eng2", "eng3")
      private val emails = Map(
        "eng1" -> "eng1@abc.com",
        "eng2" -> "eng2@abc.com",
        "eng3" -> "eng3@abc.com"
      )

      private def processEvent(pagerEvent:PagerEvent) : String ={
        val engineeringIndex = Random.nextInt(3)
        val engineer = engineers(engineeringIndex)
        val engineerEmail = emails(engineer)
        println(s"actor: email:$engineerEmail event: $pagerEvent ")
        //Thread.sleep(1000)
        engineerEmail
      }



      override def receive: Receive = {
        case pagerEvent: PagerEvent =>
          sender() ! processEvent(pagerEvent)
      }
    }//actor

    val pagerActor = system.actorOf(Props[PagerActor])
    val infraEvents = eventSource//.filter(_.application=="app1")
    val pagedEMailSink = Sink.foreach[String](email => println(s"sink: notification sent to $email"))
    implicit  val timeout = Timeout (2 seconds)
    val pagedEngineerEmails = infraEvents.map{ e=>
      println("source app:",e.application)
      e
    }.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String] )

    pagedEngineerEmails.to(pagedEMailSink).run()

  }
}

object  OnoNode extends App{
  val mainThread = Thread.currentThread
  val conf: Config = ConfigManager.read(args)

  implicit val system = ActorSystem("SingleRequest")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
  //implicit val materializer = ActorMaterializer()

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)


  def main(): Unit ={

    ExternalService.run()

//    startStreaming1()
//    startActorStreaming()
//    startHttpService()

  }

  def a()(implicit a:Int) = {

  }

  def startActorStreaming(): Unit = {



    class SimpleActor extends Actor with ActorLogging {
      override def receive: Receive = {
        case s:String =>
          log.info(s"actor received a string: $s")
          sender() ! s"send back $s"
        case n: Int =>
          log.info(s"actor received an integer: $n")
          println(s"actor.receive: $n")
          sender() ! (2 * n )
        case _ => ;
      }
    }


    val simpleActor = system.actorOf(Props[SimpleActor])
    implicit  val timeout = Timeout(2 seconds)
    val numbers = Source (1 to 10)
    val sink = Sink.foreach[Int](println)

    /*
    actor as a flow
     */
//    val actorFlow = Flow[Int].ask(parallelism = 4)(simpleActor)
//    numbers.async.via(actorFlow).async.to(Sink.ignore).run()

    /*
    actor as a source
     --skipped--
     */

    /*
    actor as sink
     */


    class MySinkActor extends Actor with ActorLogging {
      override def receive: Receive = {
        case StreamInit =>
          log.info("stream initialized")
          sender() ! StreamAck
        case StreamComplete =>
          log.info("stream completed")
          context.stop(self)
        case StreamFail(ex) =>
          log.warning(s"stream failed: $ex")
        case message =>
          log.info(s"message received: $message")
          sender() ! StreamAck
      }
    }

    val sinkActorRef = system.actorOf(Props[MySinkActor])

    val sinkByActor = Sink.actorRefWithAck[Int](
      sinkActorRef,
      onInitMessage = StreamInit,
      ackMessage = StreamAck,
      onCompleteMessage = StreamComplete,
      onFailureMessage = throwable => StreamFail(throwable)
    )

    Source(1 to 10).to(sinkByActor).run()


  }
  def startStreaming1():Unit = {

//    implicit val system = ActorSystem("SingleRequest")
//    implicit val materializer = ActorMaterializer()

    val source = Source(1 to 10)
    val sink = Sink.reduce[Int]((a,b)=>a+b)
    source.runWith(sink).onComplete {
      case Success(sum) => println(s"sum: $sum")
      case Failure(_) => println("stream failure!")
    }

  }

  def startHttpService(): Unit = {

    val masterUri = conf.getString("ono.cluster.master.masterUri")

    //val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://10.96.5.6:8080/home"))
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = masterUri))
    responseFuture
      .onComplete {
        case Success(res) => println(res)
        case Failure(ex)   =>   println(ex)

      }

  }




  main()


  


}
