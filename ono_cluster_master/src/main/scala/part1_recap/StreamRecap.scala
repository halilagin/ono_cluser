package part1_recap

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.util.{Failure, Success}

object StreamRecap extends App {
  println("Akka Stream Recap!")

  implicit val system = ActorSystem("AkkaStream")
  implicit val materilizer = ActorMaterializer()

  val source = Source(1 to 100)
  val sink = Sink.foreach[Int](println)
  val flow = Flow[Int].map(x => x + 1)

  val runnableGraph = source.via(flow).to(sink)
  runnableGraph.run()


  val sumSink = Sink.fold[Int, Int](0)((currentSum, element) => currentSum + element)
  val sumFuture = source.runWith(sumSink)


  import scala.concurrent.ExecutionContext.Implicits.global

  sumFuture.onComplete {
    case Success(sum) => println(s"sum: $sum")
    case Failure(exception) => println(s"ex: $exception")
  }


}
