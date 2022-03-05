package udemy.cluster.onokube4

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props, ReceiveTimeout}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import udemy.cluster.ono.OnoUtil

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

trait OnoSerializable
trait OnoApiCommand extends OnoSerializable
trait OnoInternalCommand extends OnoSerializable

object OnoClusteringDomain {
  val clusterConfigPath:String = "udemy/ono/OnoKubeClusteringV4.conf"
  val clusterName:String = "OnoKubeCluster4"
  val masterRoleName:String = "master"
  val workerRoleName:String = "worker"
  val apiServerRoleName:String = "api_server"
  val masterName:String = "master"
  val workerName:String = "worker"

  val seedNode1Name:String = "SeedNode1"
  val seedNode2Name:String = "SeedNode2"
  val seedNodeRoleName:String = "SeedNode"


  //messages between actors
  case class AskActiveState(state:Option[Boolean]) extends OnoInternalCommand
  case class IncrementOffset(inc:Int) extends OnoInternalCommand
  case class GetOffset() extends OnoInternalCommand
  case class SetOffset(offset:Int) extends OnoInternalCommand
  case class StartProcessFileInternal(initialOffset:Int) extends OnoInternalCommand

  case class AssignChunkToWorker(lastIdx:Int,  line:String, aggregator:ActorRef) extends OnoInternalCommand
  case class StartProcessLine(lastIdx:Int, line: String) extends OnoInternalCommand

  case class ProcessLine(lastIdx:Int,  line: String, aggregator:ActorRef) extends OnoInternalCommand
  case class ProcessLineResult(lastIdx:Int,  count: Int) extends OnoInternalCommand

  //messages from http to cluster actors
  case class RegisterWorker(address:Address, ref:ActorRef) extends OnoApiCommand
  case class CommandMasterNodeStart(filePath:String, offset: Int) extends OnoApiCommand
  case class CommandMasterNodeStop() extends OnoApiCommand
  case class CommandMasterNodeGetOffset() extends OnoApiCommand
  case class CommandMasterNodeResume() extends OnoApiCommand

}

class OnoClusteringMailBox (settings: ActorSystem.Settings, config:Config) extends UnboundedPriorityMailbox (
  PriorityGenerator {
    case _: MemberEvent => 0
    case _: OnoApiCommand => 2
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


object MasterNodeWorkingStatus extends Enumeration
{
  type Main = Value

  // Assigning values
  val Initialized = Value(0, "Thriller")
  val Stopped = Value(1,"Horror")
  val Resumed = Value(2,"Comedy")
  val fourth = Value(3, "Romance")


}

class OnoClusterMaster extends Actor with ActorLogging {
  import OnoClusteringDomain._
  import context.dispatcher
  implicit val timeout =  Timeout(10 seconds)

  val aggregator = context.actorOf(Props[Aggregator], "aggregator")
  val cluster = Cluster(context.system)
  var active:Boolean = false
  var offset:Int = 0
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

  override def receive:Receive = handleMemberEvents
    .orElse(handleRegisterWorker)
    .orElse(handleProcessingMessages)

  def handleMemberEvents:Receive = {
    case MemberUp(member) if member.hasRole("worker")  =>
      println("MemberUp", member.address)
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


    //  def handleMessagesWhileOnline(active:Boolean):Receive = {
//    case CommandMasterNodeDeactivate(dummy) =>
//      println("MasterNode: deactivated")
//      context.become(handleMemberEvents.orElse(handleRegisterWorker).orElse(handleMessagesWhileOffline(false)))
//  }

  var sourceFilePath:String = ""

  def handleProcessingMessages: Receive = {
      case IncrementOffset(inc:Int) =>
        offset += inc
      case g: GetOffset =>
        sender ! Some(offset)
      case SetOffset(newOffset) =>
        offset = newOffset
      case c: CommandMasterNodeGetOffset =>
        sender ! Some(offset)

      case AskActiveState(state) => state match {
        case Some(s) =>
          log.info(s"master: state changed: before:$active, after: $s")
          active = s
          sender ! Some(active)
        case None => sender ! Some(active)
      }

      case c@CommandMasterNodeStart(filePath, offset) =>
        sourceFilePath = filePath
        log.info(s"master:CommandMasterNodeStart, $filePath, $sourceFilePath, $c")
        //context.become(handleMemberEvents.orElse(handleRegisterWorker).orElse(handleProcessingMessages(filePath,  offset)))
        (self ? AskActiveState(Some(true)) ).foreach(a => self ! StartProcessFileInternal( 0))


      case c@CommandMasterNodeResume() =>
        log.info(s"master:CommandMasterNodeResume,  $sourceFilePath, $c")
        //context.become(handleMemberEvents.orElse(handleRegisterWorker).orElse(handleProcessingMessages(filePath,  offset)))
        (self ? AskActiveState(Some(true)) ).foreach(a => self ! StartProcessFileInternal( offset))


      case c:CommandMasterNodeStop =>
        log.info("MasterNode: deactivated")
        //context.become(handleMemberEvents.orElse(handleRegisterWorker).orElse(handleProcessingMessages(filePath,  offset)))
        (self ? AskActiveState(Some(false)) )



      case StartProcessFileInternal( initialOffset) =>

        val lines = scala.io.Source.fromFile(sourceFilePath).getLines().toList
        (self ? GetOffset()).mapTo[Option[Int]].onComplete{
          case Success(Some(offset)) =>
            val strings = lines.slice(offset, lines.length)
            log.info(s"master: offset:$offset, lines.length:${lines.length}, strings.length:${strings.length}")
            //println("filePath.strings.length", strings.length)

            //open new
            //child actor de calistir. child actor adresini sakla. active=false geldiginde child actor interrupt et.
            strings.zipWithIndex.foreach { case (lineString, lineIdx)  =>
              (self ? AskActiveState(None)).map {
                case Some(true) => self ! AssignChunkToWorker(strings.length - 1, lineString, aggregator)
                case None => log.info(s"master state is none") //error state
              }
            }//foreach
          case Failure(ex) => log.error(s"error received $ex")

        }



      case a@AssignChunkToWorker(lastIdx,  lineString, aggregator) =>
        val workerList = workers.keys.toList
        val workersExist = workerList.size > 0
        if (workersExist) {
          val workerAddress = workerList(Random.nextInt(workerList.size))
          val worker = workers(workerAddress)
          // bellekten çekiyor 8 işlenmeyen.
          // command s3://ahmet.txt
          //we pass to self to get out of the thread so that master can receive some events

          (self ? AskActiveState(None) ).map {
            case Some(true) =>    worker !  ProcessLine(lastIdx, lineString, aggregator)
            case None       =>    log.error("master active state is none")
          }

        }

      case m: Any => println("unmanaged event:", m)
  }
}


class OnoClusterWorker extends Actor with ActorLogging {
  import OnoClusteringDomain._
  implicit val timeout =  Timeout(3 seconds)
  import context.dispatcher
  import scala.concurrent.ExecutionContext.Implicits.global


  def passCommandToMasterNode(member: akka.cluster.Member, command: OnoApiCommand): Unit = {
    val address = s"${member.address}/user/master"

  }
  def work(text:String): Int = {
    val length  = text.split(" ").length


    length
  }

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
  implicit val timeout =  Timeout(3 seconds)
  import context.dispatcher
  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Receive = online(0, 0)

  def online( idx:Int, total:Int): Receive = {
    case ProcessLineResult(lastIdx, count) =>
      if (total % 100 == 0)
        log.info(s"Aggregator:  processing line $idx/$lastIdx, total: $total")

      val newTotal = count + total
      if (idx==lastIdx)
        log.info(s"$idx, TOTAL COUNT: ${newTotal}")
      else {
        val parentPath = context.parent.path
        val address = s"${parentPath}"
        context.actorSelection(address).resolveOne.onComplete {
          case Success(masterRef) =>
            masterRef ! IncrementOffset(1)
          case Failure(ex) =>
            println(s"Aggregator:resolve:master:failure:$ex")
        }
        context.become(online( idx+1, newTotal))
      }

    case ReceiveTimeout =>
      log.info(s"TOTAL COUNT: $total")
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
          .withFallback(ConfigFactory.load(OnoClusteringDomain.clusterConfigPath))
      case _ =>
        config = partialConfig(role, hostname, port)
          .withFallback(ConfigFactory.load(OnoClusteringDomain.clusterConfigPath))
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

  def createMaster(config: Config): ActorRef = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.master.name"),
      config.getString("ono.cluster.master.roleName"),
      config.getString("ono.cluster.master.hostname"),
      config.getInt("ono.cluster.master.port"),
      Props[OnoClusterMaster]
    )

  }


  def createWorkers(config: Config): ActorRef = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.worker.name"),
      config.getString("ono.cluster.worker.roleName"),
      config.getString("ono.cluster.worker.hostname"),
      config.getInt("ono.cluster.worker.port"),
      Props[OnoClusterWorker]
    )
  }

  def createClient(config: Config): ActorRef = {
    createNode(config.getString("ono.cluster.name"),
      config.getString("ono.cluster.client.name"),
      config.getString("ono.cluster.client.roleName"),
      config.getString("ono.cluster.client.hostname"),
      config.getInt("ono.cluster.client.port"),
      Props[OnoClusterClient]
    )
  }

}//class




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
          .withFallback(ConfigFactory.load(OnoClusteringDomain.clusterConfigPath))

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
          .withFallback(ConfigFactory.load(OnoClusteringDomain.clusterConfigPath))

        val system = ActorSystem(OnoClusteringDomain.clusterName, config)
        system.actorOf(Props[OnoClusterClient], "client")
      case _ => println("OnoClient: no site address found")
    }
  }
}


object OnoClusterSeedNodes1 extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
  OnoClusterSeedNodes.createSeedNode1(config)
}
//object OnoClusterSeedNodes2 extends App {
//  OnoClusterSeedNodes.createSeedNode2
//}

object OnoClusterCreateMaster extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
  OnoClusterSeedNodes.createMaster(config)
}

object OnoClusterCreateWorkers extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
  OnoClusterSeedNodes.createWorkers(config)
}

object AdditionalWorker1 extends App {
  AdditionalWorker.up
}

object RunOnoClusterClient1 extends App {
  val config = ConfigFactory.load(OnoClusteringDomain.clusterConfigPath)
  OnoClusterSeedNodes.createClient(config)
}

