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

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

// step 1
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.concurrent.duration._
import scala.language.postfixOps
case class Player(nickname: String, characterClass: String, level: Int)
case class SingleObject(value:Int)
// step 2
trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat1 = jsonFormat3(Player)
  implicit val playerFormat2 = jsonFormat1(SingleObject)
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
  import scala.concurrent.ExecutionContext.Implicits.global
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
    case StartProcess(a) =>
      activateMasterNode(meAsMember)

    case c@CommandMasterNodeStop() =>
      println("http api server: CommandMasterNodeDeactivate received")
      passCommandToMasterNode(meAsMember, c)
    case CommandMasterNodeStart =>
      println("http api server: CommandMasterNodeActivate received")
      activateMasterNode(meAsMember)
    case c@CommandMasterNodeResume() =>
      println("http api server: CommandMasterNodeResume received")
      passCommandToMasterNode(meAsMember, c)
    case c@CommandMasterNodeGetOffset() =>
      val address = s"${meAsMember.address}/user/master"
      //val offset = context.actorSelection(address).resolveOne.map(x => x ? c).mapTo[Option[Int]]

      /*
      val resolveOneFuture = context.actorSelection(address).resolveOne
      val masterActorRef:ActorRef = Await.result(resolveOneFuture, 3 seconds)
      val masterReturnedOffset = (masterActorRef ? c).mapTo[Option[Int]]
      val offset = Await.result(masterReturnedOffset, 3 seconds)
      sender ! offset
      */
/*
      val masterReturnedOffset:Future[Option[Int]] = context.actorSelection(address).resolveOne.flatMap( masterActorRef => (masterActorRef ? c).mapTo[Option[Int]] )
      val offset = Await.result(masterReturnedOffset, 3 seconds)
      sender ! offset
*/

      val masterReturnedOffset:Future[Option[Int]] = context.actorSelection(address).resolveOne.flatMap( masterActorRef => (masterActorRef ? c).mapTo[Option[Int]] )
      val offset = Await.result(masterReturnedOffset, 3 seconds)
      sender ! offset

  }

  def activateMasterNode(member: akka.cluster.Member): Unit = {
    println("client:send to master:CommandMasterNodeActivate", member.address)
    val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
    val filePath = config.getString("ono.cluster.master.filePath")
    println("master:handleJob:filePath", filePath)
    passCommandToMasterNode(member, CommandMasterNodeStart(filePath,0))
  }

  def passCommandToMasterNode(member: akka.cluster.Member, command: OnoApiCommand): Unit = {
    val address = s"${member.address}/user/master"
    context.actorSelection(address).resolveOne.onComplete {
      case Success(masterRef) =>
        masterRef ! command
      case Failure(ex) =>
        println(s"OnoClusterClient:resolve:master:failure:$ex")
    }
  }


//  def askCommandToMasterNode(member: akka.cluster.Member, command: OnoApiCommand): Future[Option[Any]] = {
//    val address = s"${member.address}/user/master"
//    context.actorSelection(address).resolveOne.map(x => x ? command).mapTo[Option[Any]]
//  }


}



object KubeAkkaApiHttpServer extends App
  with PlayerJsonProtocol
  with SprayJsonSupport
{


  def run()(implicit apiServerActorRef: ActorRef): Future[Http.ServerBinding] = {
    import OnoClusterApiServerDomain._
    import OnoClusteringDomain._

    implicit val system = ActorSystem ("OnoClusterApiServer")
    implicit val materializer = ActorMaterializer ()
    import akka.http.scaladsl.server.Directives._

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
            path ("resume_process") {
              println("resumeprocess executed!")
              apiServerActorRef ? CommandMasterNodeResume()
              complete(StatusCodes.OK)
            }
          } ~
          get {
            path ("start_process") {
              println("activate_process executed!")
              apiServerActorRef ? CommandMasterNodeStart
              complete(StatusCodes.OK)
            }
          }~
          get {
            path ("stop_process") {
              println("ApiServer: stop_process executed!")
              apiServerActorRef ? CommandMasterNodeStop()
              complete(StatusCodes.OK)
            }
          } ~
          get {
            path ("get_offset") { ctx =>
             // val eventualMaybeInt =((apiServerActorRef ? GetOffset()).mapTo[Option[Int]]
              //val currentOfsset:Option[Int] = Await.result(eventualMaybeInt, 2 seconds)
              //ctx.complete( SingleObject(5))
              val offsetResult:Future[SingleObject] = (apiServerActorRef ? CommandMasterNodeGetOffset()).mapTo[Option[Int]].map(x => SingleObject(x.getOrElse(-1)))
              ctx.complete(offsetResult)

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
  private val onoClusterClientRef: ActorRef = OnoClusterSeedNodes.createClient(config)

  implicit val onoClusterClient: ActorRef = onoClusterClientRef

  private val apiServer: Future[Http.ServerBinding] = KubeAkkaApiHttpServer.run()(onoClusterClient)


}
