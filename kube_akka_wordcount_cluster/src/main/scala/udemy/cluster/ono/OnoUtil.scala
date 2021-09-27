package udemy.cluster.ono

import java.net.NetworkInterface
import scala.jdk.CollectionConverters._

object OnoUtil {
  //finds an interface of 172.*, 192.168.*, 10.* at local
  def siteLocalAddress(): Option[String] =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(_.isSiteLocalAddress)
      .map(_.getHostAddress)
}
