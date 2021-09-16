package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

object AkkaRecap extends App {
  println("Akka Recap!")


  class SimpleActor extends Actor with Stash with ActorLogging {
    override def receive: Receive = {
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "myChild")
        childActor ! "hello"
      case "stashThis" =>
        stash()
      case "change handler NOW" =>
        unstashAll()
        context.become(newHandler)
      case "change" => context.become(newHandler)
      case message => println(s"I received $message")
    }

    def newHandler: Receive = {
      case message => println(s"newHandler -> $message")
    }

    override def preStart(): Unit = {
      log.info("I'm starting!")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop

    }
  }

  val system = ActorSystem("AkkaRecap")
  val actor = system.actorOf(Props[SimpleActor])
  actor ! "hello"
  actor ! PoisonPill

  import system.dispatcher

  import scala.concurrent.duration._
  import scala.language.postfixOps

  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "delayed happy birthday!"
  }
  implicit val timeout = Timeout(3 seconds)
  val future = actor ? "question"

  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)


}
