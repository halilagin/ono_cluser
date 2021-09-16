package part2_lowlevelhttp

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class Guitar(make:String, model:String, quantity: Int = 0)

object GuitarDB {
  case class CommandCreateGuitar(guitar: Guitar)
  case class CommandFindGuitar(id: Int)
  case class CommandGuitarsInStock(inStock:Boolean)
  case class CommandAddQuantity(id: Int, quantity: Int)
  case class CommandFindAllGuitars()
  case class EventGuitarCreated(id:Int)
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}

class GuitarDB extends Actor with ActorLogging  {
  import GuitarDB._
  var guitars:Map[Int, Guitar] = Map()
  var currentGuitarId:Int = 0

  override def receive: Receive = {
    case CommandFindAllGuitars =>
      log.info("Searcing for all guitars")
      sender() ! guitars.values.toList
    case CommandFindGuitar(id) =>
      log.info(s"fetching guitar by id $id")
      sender() ! guitars.get(id)
    case CommandCreateGuitar(guitar) =>
      log.info(s"adding a new guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! EventGuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case CommandAddQuantity(id:Int, quantity:Int) =>
      log.info(s"adding quantity $quantity to guitar with id $id")
      val guitar:Option[Guitar] = guitars.get(id)
      val newGuitar: Option[Guitar] = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }

      newGuitar.foreach { guitar =>
        guitars = guitars + (id -> guitar)
      }
      sender() ! newGuitar
    case CommandGuitarsInStock(inStock:Boolean) =>
      log.info(s"""find guitars ${if (inStock) "in" else "out"} of stock""")
      if (inStock)
        sender ! guitars.values.filter(_.quantity>0)
      else
        sender ! guitars.values.filter(_.quantity==0)

  }
}

object LowLevelJson extends App with GuitarStoreJsonProtocol  {

  implicit val system = ActorSystem("ActorSystem")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import GuitarDB._

  val simpleGuitar = Guitar("Fender",  "stratocaster")
  println(simpleGuitar.toJson.prettyPrint)


  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "stratocaster",
      |  "quantity": 3
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1"),
  )

  guitarList.foreach{guitar =>
    guitarDB ! CommandCreateGuitar(guitar)
  }


  implicit val  defaultTimeout = Timeout(2 seconds)

  def fetchGuitar(query: Query): Future[HttpResponse] = {
    val id = query.get("id").map(_.toInt)
    id match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id:Int)  =>
        val guitarFuture: Future[Option[Guitar]] = (guitarDB ? CommandFindGuitar(id)).mapTo[Option[Guitar]]
        val eventualResponse: Future[HttpResponse] = guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
        eventualResponse
    }


  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"),_,_,_) =>

      val guitarId: Option[Int] = uri.query().get("id").map(_.toInt)
      val guitarQuantity: Option[Int] = uri.query().get("quantity").map(_.toInt)

      val validGuitarFutureResponse: Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        println(id, quantity)
        val newGuitarFuture: Future[Option[Guitar]] = (guitarDB ? CommandAddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarFutureResponse.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"),_,_,_) =>
      val inStockOption: Option[Boolean] = uri.query().get("inStock").map(_.toBoolean)

      inStockOption match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDB ? CommandGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }



    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"),_,_,_) =>
      val query = uri.query()
      if (query.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? CommandFindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map{ guitars =>
          HttpResponse(
            status=StatusCodes.OK,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else
        fetchGuitar(query)


    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) => {
      val entityStrictFuture = entity.toStrict(3 seconds)
      entityStrictFuture.flatMap { strictEntity =>
        val entityString = strictEntity.data.utf8String
        val a:Guitar = """
          {  "make": "Taylor",  "model": "914"}
        """.parseJson.convertTo[Guitar]
        println("halil", entityString, a)

        val guitar = entityString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture = (guitarDB ? CommandCreateGuitar(guitar) ).mapTo[EventGuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }
    }

    case request:HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(
          status=StatusCodes.NotFound
        )
      }

  }


  Http().bindAndHandleAsync(requestHandler, "localhost", 8000)
}
