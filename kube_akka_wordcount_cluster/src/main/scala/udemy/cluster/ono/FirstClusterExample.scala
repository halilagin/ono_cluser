package udemy.cluster.ono

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props, ReceiveTimeout}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import udemy.cluster.onokube
import udemy.cluster.onokube.{Aggregator, OnoClusterMaster}

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}
import scala.language.postfixOps

trait OnoSerializable

object OnoClusteringDomain {
  case class ProcessFile(filePath: String) extends onokube.OnoSerializable
  case class AssignChunkToWorker(lastIdx:Int, line:String, aggregator:ActorRef) extends onokube.OnoSerializable
  case class ProcessLine(lastIdx:Int, line: String, aggregator:ActorRef) extends onokube.OnoSerializable
  case class ProcessLineResult(lastIdx:Int, count: Int) extends onokube.OnoSerializable
  case class RegisterWorker(address:Address, ref:ActorRef) extends onokube.OnoSerializable
  case class OnoMemberUp(member:Member) extends onokube.OnoSerializable
}

class OnoClusteringMailBox (settings: ActorSystem.Settings, config:Config) extends UnboundedPriorityMailbox (
  PriorityGenerator {
    case _: MemberEvent => 0
    case _ => 4
  }
)

class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
  def address = system.provider.getDefaultAddress
}

object RemoteAddressExtension extends ExtensionId[onokube.RemoteAddressExtensionImpl]  with ExtensionIdProvider {
  override def lookup = onokube.RemoteAddressExtension
  override def createExtension(system: ExtendedActorSystem) = new onokube.RemoteAddressExtensionImpl(system)
  override def get(system: ActorSystem): onokube.RemoteAddressExtensionImpl = super.get(system)
}


class OnoClusterMaster extends Actor with ActorLogging {
  import udemy.cluster.onokube.OnoClusteringDomain._
  import context.dispatcher
  implicit val timeout =  Timeout(3 seconds)

  val cluster = Cluster(context.system)

  var workers: Map[Address, ActorRef] = Map()
  var pendingMembers: Map[Address, ActorRef] = Map()

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

  override def receive:Receive = handleMemberEvents.orElse(handleRegisterWorker).orElse(handleJob)

  def handleMemberEvents:Receive = {
    case MemberUp(member) =>
      println("MemberUp", member.address)
      if (member.hasRole("worker"))
        self ! OnoMemberUp(member)
    case OnoMemberUp(member)  =>
      println("OnoMemberUp", member.address)
      if (pendingMembers.contains(member.address))
        pendingMembers = pendingMembers - member.address
      else {
        val address = s"${member.address}/user/worker"
        context.actorSelection(address).resolveOne.onComplete {
          case Success(ref) =>
            self ! RegisterWorker(member.address,  ref)
          case Failure(ex) =>
            println(s"master:resolve:failure:$ex")
        }
      }
    case MemberRemoved(member, previousStatus) =>
      pendingMembers = pendingMembers - member.address
      workers = workers - member.address
      println("MemberRemoved",member.address)

    case UnreachableMember(member) if member.hasRole("worker") =>
      val workerOption = workers.get(member.address)
      workerOption.foreach { ref =>
        pendingMembers = pendingMembers + (member.address -> ref)
      }

    case m: MemberEvent =>
      println(s"new member: $m")

  }

  def handleRegisterWorker: Receive = {
    case RegisterWorker(address, actorRef) =>
      println("RegisterWorker",address)
      workers = workers ++ Map(address -> actorRef)
  }

  def handleJob:Receive = {

    case ProcessFile(filePath) =>
      val strings = scala.io.Source.fromFile(filePath).getLines().toList
      //println("filePath.strings.length", strings.length)
      val aggregator = context.actorOf(Props[Aggregator], "aggregator")
      strings.foreach { line  =>
        self ! AssignChunkToWorker(strings.length-1, line, aggregator)
      }//foreach
    case AssignChunkToWorker(lastIdx, line, aggregator) =>
      val workerList = workers.keys.toList
      val workersExist = workerList.size > 0
      if (workersExist) {
        val workerAddress = workerList(Random.nextInt(workerList.size))
        val worker = workers(workerAddress)
        println("sending to worker:", workerAddress)
        //we pass to self to get out of the thread so that master can receive some events
        worker !  ProcessLine(lastIdx, line, aggregator)
        Thread.sleep(20)
      }

    case m: Any => println("unmanaged event:", m)
  }
}


class OnoClusterWorker extends Actor with ActorLogging {
  import udemy.cluster.onokube.OnoClusteringDomain._

  def work(text:String): Int = text.split(" ").length

  override def receive: Receive = {

    case ProcessLine(lastIdx, line, aggregator) =>

      //val address = self.path.toStringWithAddress(self.path.address)
      val address = onokube.RemoteAddressExtension(context.system).address
      val workResult:Int = work(line)
      //Thread.sleep(500)
      aggregator ! ProcessLineResult(lastIdx, workResult)
  }
}

class Aggregator extends Actor with ActorLogging {
  import udemy.cluster.onokube.OnoClusteringDomain._

  override def receive: Receive = online(0,0)

  def online(idx:Int, total:Int): Receive = {
    case ProcessLineResult(lastIdx, count) =>
      val newTotal = count + total
      if (idx==lastIdx)
        println(s"$idx, TOTAL COUNT: ${newTotal}")
      else
        context.become(online(idx+1, newTotal))

    case ReceiveTimeout =>
      println(s"TOTAL COUNT: $total")
      context.setReceiveTimeout(Duration.Undefined)
  }
}

object OnoClusterSeedNodes extends App {
  import udemy.cluster.onokube.OnoClusteringDomain._
  def createNode(name:String, role:String, port:Int, props:Props): Unit = {
    val config = ConfigFactory.parseString(
      s"""
         | akka.cluster.roles = ["$role"]
         | akka.remote.artery.canonical.port = $port
         | akka.remote.artery.canonical.hostname = "127.0.0.1"
         |""".stripMargin
    ).withFallback(ConfigFactory.load("udemy/ono/OnoClustering.conf"))

    val system = ActorSystem("OnoCluster1", config)
    import system.dispatcher
    val actor =  system.actorOf(props, name)

    if (name=="master"){
      system.scheduler.scheduleOnce(10 seconds) {
        actor ! ProcessFile("ono_cluster_master/src/main/resources/lipsum.txt")
      }
    }

  }

  createNode("master", "master", 13551, Props[OnoClusterMaster])
  createNode("worker", "worker", 13552, Props[onokube.OnoClusterWorker])
  createNode("worker", "worker", 13553, Props[onokube.OnoClusterWorker])
  //AdditionalWorker.up
}


object AdditionalWorker extends App {
  def up ={
    val config = ConfigFactory.parseString(
      s"""
         |akka.cluster.roles = ["worker"]
         |akka.remote.artery.canonical.port = 13601
         |akka.remote.artery.canonical.hostname = "127.0.0.1"
         """.stripMargin)
      .withFallback(ConfigFactory.load("udemy/ono/OnoClustering.conf"))

    val system = ActorSystem("OnoCluster1", config)
    system.actorOf(Props[onokube.OnoClusterWorker], "worker")
  }
  up
}
