package udemy.cluster.onokube4

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberUp, UnreachableMember}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

// step 1
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps
case class Player(nickname: String, characterClass: String, level: Int)

// step 2
trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object OnoClusterApiServerDomain {
  case class HealthCheck()
  case class CommandDummy(a:Int)
  case class StartProcess(a:Int)

}

class OnoClusterClient extends Actor with ActorLogging {
  import OnoClusteringDomain._
  import OnoClusterApiServerDomain._
  implicit val timeout =  Timeout(3 seconds)
  import context.dispatcher

  val cluster = Cluster(context.system)
  var meAsMember: akka.cluster.Member = null

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {

    case MemberUp(member) if member.hasRole(OnoClusteringDomain.masterRoleName) =>
      println("Master is up", member.address)
      meAsMember = member
      //actor ! ProcessFile(OnoClusteringDomain.inputFilePath)
      startProcessFile(member)
    case StartProcess(a) =>
      startProcessFile(meAsMember)
  }

  def startProcessFile(member: akka.cluster.Member): Unit = {
    val address = s"${member.address}/user/master"
    context.actorSelection(address).resolveOne.onComplete {
      case Success(masterRef) =>
        println("client:send to master:StartProcessFile", member.address)

        masterRef ! StartProcessFile("")
      case Failure(ex) =>
        println(s"OnoClusterClient:resolve:master:failure:$ex")
    }
  }


}



object KubeAkkaApiHttpServer {


  def run()(implicit apiServerActorRef: ActorRef): Future[Http.ServerBinding] = {
    import OnoClusterApiServerDomain._

    implicit val system = ActorSystem ("OnoClusterApiServer")
    implicit val materializer = ActorMaterializer ()

    import system.dispatcher

    implicit val timeout = Timeout (3 seconds)

    val routeSkel =
    pathPrefix ("api" / "player") {
      get {
      path ("class" / Segment) { characterClass =>
        complete (StatusCodes.OK)
      } ~
      (path (Segment) | parameter ('nickname) ) { nickname =>
        complete (StatusCodes.OK)
      } ~
        pathEndOrSingleSlash {
        complete (StatusCodes.OK)
      }
      } ~
      post {
        complete (StatusCodes.OK)
      } ~
      delete {
        complete (StatusCodes.OK)
      }
    } ~
      pathPrefix(pm ="api" / "test"){
        get {
          path("check") {
            apiServerActorRef ? CommandDummy(5)
            complete(StatusCodes.OK)
          }
        } ~
          get {
            path ("hello") {
              complete(StatusCodes.OK)
            }
          } ~
          get {
            path ("start_process") {
              println("startprocess executed!")
//              apiServerActorRef ? StartProcess(4)
              complete(StatusCodes.OK)
            }
          }
      }


    val eventualBinding: Future[Http.ServerBinding] = Http ().bindAndHandle (routeSkel, "0.0.0.0", 8080)
    eventualBinding
  }

}

object KubeAkkaApiHttpServerApp extends App
  with PlayerJsonProtocol
  with SprayJsonSupport {
  implicit val system = ActorSystem ("OnoClusterApiServer")
  implicit val materializer = ActorMaterializer ()

  import system.dispatcher

  implicit val timeout = Timeout (3 seconds)


  //startup OnoClusterApiServer Node
  val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
  private val apiServerActorRef: ActorRef = OnoClusterSeedNodes.createClient(config)

  implicit val apiServerActor: ActorRef = apiServerActorRef

  private val apiServer: Future[Http.ServerBinding] = KubeAkkaApiHttpServer.run()(apiServerActor)


}
