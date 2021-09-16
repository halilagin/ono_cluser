package part1_recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {
  println("test sdf !")


  class Animal

  trait Carnivore {
    def eat(animal: Animal): Unit
  }

  object Carnivore

  abstract class List[+A]

  1.+(2)

  val anIncrementer: Int => Int = (a: Int) => a + 1
  val list = List(1, 2, 3).map(anIncrementer)
  list.map(println)


  val unknown: Any = 2

  val found = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    throw new NotImplementedError()
  } catch {
    case e: NotImplementedError =>
      println("no implemented!")
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  val future = Future {
    42
  }

  future.onComplete {
    case Success(value) => println(s"future completed with value $value")
    case Failure(exception) => println(s"future failed with value $exception")
  }


  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 999
  }

  type AkkaReceive = PartialFunction[Any, Unit]

  def receive: AkkaReceive = {
    case 1 => println("fe")
    case 2 => println("sdf")
    case _ => println("any")
  }

  receive("Fer")


  implicit val timeout = 3000

  def setTimeout(f: () => Unit)(implicit timeout: Int) = f()

  setTimeout(() => println(s"timouting"))


  case class Person(name: String) {
    def greet = println(s"welcome $name")
  }

  implicit def fromStringToPerson(name: String) = Person(name)

  "halil".greet

  implicit class Dog(name: String) {
    def bark = println(s"$name, bark!")
  }

  "Lassie".bark

  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)

  List(1, 2, 3).sorted.map(println)


  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((p1, p2) => p1.name.compareTo(p2.name) > 0)
  }

  List(Person("Ahmet"), Person("Veli")).sorted.map(_.name).map(println)
}
