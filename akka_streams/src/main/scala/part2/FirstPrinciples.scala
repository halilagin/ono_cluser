package part2

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}


object FirstPrinciples extends App{

  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()

  Source.single("hello, akka stream!").to(Sink.foreach(println)).run()

  val source = Source (1 to 10)
  val sink = Sink.foreach[Int](println)
  val graph = source.to(sink)
  graph.run()

  val flow = Flow[Int].map(x => x+1)
  val sourceWithFlow = source.via(flow)
  val sinkWithFlow = flow.to(sink)

  println("source with flow to sink")
  sourceWithFlow.to(sink).run()


  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1,2,3,4,5))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(42))

  //sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int]
  val foldSink = Sink.fold[Int, Int](0)((a,b)=>a+b)

  //flows
  val mapFlow = Flow[Int].map(x => x*2)
  val takeFlow = Flow[Int].take(5)

  println("doubleflow graph")
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  println("keep right")
  val f1 = Source(1 to 29).toMat(Sink.last)(Keep.right).run()
  f1.onComplete{
    case Success(value) => println(value)
    case Failure(exception) => println(exception)
  }
}
