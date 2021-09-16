package part3_highlevelhttp

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayerByClass(className: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess

}

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("get all players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"get player by nickname $nickname")
      players.get(nickname)
    case GetPlayerByClass(className) =>
      sender() ! players.values.filter(_.characterClass==className).toList
    case AddPlayer(player:Player) =>
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player:Player) =>
      players = players - player.nickname
      sender() ! OperationSuccess


  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit  val playerFormat = jsonFormat3(Player)
}

object  HigLevelHttp2 extends App
  with PlayerJsonProtocol
  with SprayJsonSupport
  {


  implicit val system = ActorSystem("system")
  implicit  val materializer = ActorMaterializer()
  import akka.http.scaladsl.server.Directives._
  import GameAreaMap._

  val gameMap = system.actorOf(Props[GameAreaMap])
  val playerList = List(
    Player("A","AA",1),
    Player("B","BB",2),
    Player("C","CC",3),
    Player("D","DD",4)
  )
  playerList.foreach{ player =>
    gameMap ! AddPlayer(player)
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit  val timeout = Timeout(2 seconds)
  val routes =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment  ) { charClass =>
          val getPlayerFuture: Future[List[Player]] = ( gameMap ? GetPlayerByClass(charClass) ).mapTo[List[Player]]
          complete(getPlayerFuture)
        } ~
          (path(Segment) | parameter(Symbol("nickname"))) { nickname =>
            complete ( (gameMap ? GetPlayer(nickname)).mapTo[Option[Player]] )
          } ~
          pathEndOrSingleSlash {
            complete ( (gameMap ? GetAllPlayers).mapTo[List[Player]] )
          }
      } ~
        post {
          entity(as[Player]) { player =>
            complete ( (gameMap ? AddPlayer(player)).map(_ => StatusCodes.OK) )
          }
        } ~
        delete {

          entity(as[Player]) { player =>
            complete ( (gameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK) )
          }
        }
    }

  Http().bindAndHandleAsync(routes, "localhost", 8081)
}


