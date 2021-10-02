package udemy.cluster.onokube3

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props, ReceiveTimeout}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import udemy.cluster.ono.OnoUtil

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

trait OnoSerializable

object OnoClusteringDomain {
//  val cluserConfigPath:String = "udemy/ono/OnoKubeClusteringV3.conf"
  val cluserConfigPath:String = "udemy/ono/OnoKubeClusteringV3.conf"
  val clusterName:String = "OnoKubeCluster3"
  val masterRoleName:String = "master"
  val workerRoleName:String = "worker"
  val masterName:String = "master"
  val workerName:String = "worker"

  val seedNode1Name:String = "SeedNode1"
  val seedNode2Name:String = "SeedNode2"
  val seedNodeRoleName:String = "SeedNode"


  case class StartProcessFile(dummy:String) extends OnoSerializable
  case class ProcessFile(filePath: String) extends OnoSerializable
  case class AssignChunkToWorker(lastIdx:Int, line:String, aggregator:ActorRef) extends OnoSerializable
  case class ProcessLine(lastIdx:Int, line: String, aggregator:ActorRef) extends OnoSerializable
  case class ProcessLineResult(lastIdx:Int, count: Int) extends OnoSerializable
  case class RegisterWorker(address:Address, ref:ActorRef) extends OnoSerializable
  case class OnoMemberUp(member:Member) extends OnoSerializable
  case class SendStartCommandToClusterMaster() extends OnoSerializable
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

object RemoteAddressExtension extends ExtensionId[RemoteAddressExtensionImpl]  with ExtensionIdProvider {
  override def lookup = RemoteAddressExtension
  override def createExtension(system: ExtendedActorSystem) = new RemoteAddressExtensionImpl(system)
  override def get(system: ActorSystem): RemoteAddressExtensionImpl = super.get(system)
}


class OnoClusterMaster extends Actor with ActorLogging {
  import OnoClusteringDomain._
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

    case StartProcessFile(_) =>
      val config = ConfigFactory.load(OnoClusteringDomain.cluserConfigPath)
      val filePath = config.getString("ono.cluster.master.filePath")
      println("master:handleJob:filePath", filePath)
      self ! ProcessFile(filePath)
    case ProcessFile(filePath) =>
      println("master:handleJob",filePath)
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
  import OnoClusteringDomain._

  def work(text:String): Int = text.split(" ").length

  override def receive: Receive = {

    case ProcessLine(lastIdx, line, aggregator) =>

      //val address = self.path.toStringWithAddress(self.path.address)
      val address = RemoteAddressExtension(context.system).address
      val workResult:Int = work(line)
      //Thread.sleep(500)
      aggregator ! ProcessLineResult(lastIdx, workResult)
  }
}

class Aggregator extends Actor with ActorLogging {
  import OnoClusteringDomain._

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


class OnoClusterSeedNode extends Actor with ActorLogging {
  import OnoClusteringDomain._
  implicit val timeout =  Timeout(3 seconds)
  import context.dispatcher

  val cluster = Cluster(context.system)
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
    case m: Any => println("SeedNode: unmanaged event:", m)
  }
}

object OnoClusterSeedNodes extends App {
  def partialConfig(role:String, hostname:String,  port:Int): Config ={
    ConfigFactory.parseString(
      s"""
         | akka.cluster.roles = ["$role"]
         | akka.remote.artery.canonical.port = $port
         | akka.remote.artery.canonical.hostname = "${hostname}"
         |""".stripMargin)
  }

  def createNode(clusterName:String, name:String, role:String, hostname:String,  port:Int, props:Props): ActorRef = {
    var config:Config = null
    OnoUtil.siteLocalAddress() match {
      case Some(siteAddress) =>
        config = partialConfig(role, siteAddress, port)
          .withFallback(ConfigFactory.load(OnoClusteringDomain.cluserConfigPath))
      case _ =>
        config = partialConfig(role, hostname, port)
          .withFallback(ConfigFactory.load(OnoClusteringDomain.cluserConfigPath))
    }
    val system = ActorSystem(clusterName, config)
    system.actorOf(props, name)
  }

  def createSeedNode1(config: Config): Unit = {
    val seedUrls:Array[String] = config.getList("akka.cluster.seed-nodes").unwrapped.toArray.map(_.toString)
    seedUrls.foreach{url =>
      val clusterName = url.substring("akka://".length, url.indexOf("@"))
      val host = url.substring(url.indexOf("@")+1,url.lastIndexOf(":") )
      val port = url.substring(url.lastIndexOf(":")+1).toInt

      createNode(clusterName,
        OnoClusteringDomain.seedNode1Name,
        OnoClusteringDomain.seedNodeRoleName,
        host,
        port,
        Props[OnoClusterSeedNode]
      )
    }


  }
//  def createSeedNode2: Unit = {
//    createNode(OnoClusteringDomain.seedNode2Name, OnoClusteringDomain.seedNodeRoleName, 14552, Props[OnoClusterMaster])
//  }

  def createMaster(config: Config): Unit = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.master.name"),
      config.getString("ono.cluster.master.roleName"),
      config.getString("ono.cluster.master.hostname"),
      config.getInt("ono.cluster.master.port"),
      Props[OnoClusterMaster]
    )

  }


  def createWorkers(config: Config): Unit = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.worker.name"),
      config.getString("ono.cluster.worker.roleName"),
      config.getString("ono.cluster.worker.hostname"),
      config.getInt("ono.cluster.worker.port"),
      Props[OnoClusterWorker]
    )
  }

  def createClient(config: Config): Unit = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.client.name"),
      config.getString("ono.cluster.client.roleName"),
      config.getString("ono.cluster.client.hostname"),
      config.getInt("ono.cluster.client.port"),
      Props[OnoClusterClient]
    )
  }


}//class

class OnoClusterClient extends Actor with ActorLogging {
  import OnoClusteringDomain._
  implicit val timeout =  Timeout(3 seconds)
  import context.dispatcher

  val cluster = Cluster(context.system)
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
      //actor ! ProcessFile(OnoClusteringDomain.inputFilePath)
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



object AdditionalWorker extends App {
  def up = {
    OnoUtil.siteLocalAddress() match {
      case Some(siteAddress) =>
        val config = ConfigFactory.parseString(
          s"""
             |akka.cluster.roles = ["worker"]
             |akka.remote.artery.canonical.port = 14601
             |akka.remote.artery.canonical.hostname = "$siteAddress"
         """.stripMargin)
          .withFallback(ConfigFactory.load(OnoClusteringDomain.cluserConfigPath))

        val system = ActorSystem(OnoClusteringDomain.clusterName, config)
        system.actorOf(Props[OnoClusterWorker], OnoClusteringDomain.workerName)
      case _ => println("there is no site address")
    }

  }
}



object RunOnoClusterClient extends App {
  def up = {
    OnoUtil.siteLocalAddress() match {
      case Some(siteAddress) =>
        val config = ConfigFactory.parseString(
          s"""
             |akka.cluster.roles = ["client"]
             |akka.remote.artery.canonical.port = 20001
             |akka.remote.artery.canonical.hostname = "$siteAddress"
             """.stripMargin)
          .withFallback(ConfigFactory.load(OnoClusteringDomain.cluserConfigPath))

        val system = ActorSystem(OnoClusteringDomain.clusterName, config)
        system.actorOf(Props[OnoClusterClient], "client")
      case _ => println("OnoClient: no site address found")
    }
  }
}


object OnoClusterSeedNodes1 extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.cluserConfigPath)
  OnoClusterSeedNodes.createSeedNode1(config)
}
//object OnoClusterSeedNodes2 extends App {
//  OnoClusterSeedNodes.createSeedNode2
//}

object OnoClusterCreateMaster extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.cluserConfigPath)
  OnoClusterSeedNodes.createMaster(config)
}

object OnoClusterCreateWorkers extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.cluserConfigPath)
  OnoClusterSeedNodes.createWorkers(config)
}
object AdditionalWorker1 extends App {
  AdditionalWorker.up
}
object RunOnoClusterClient1 extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.cluserConfigPath)
  OnoClusterSeedNodes.createClient(config)
}
