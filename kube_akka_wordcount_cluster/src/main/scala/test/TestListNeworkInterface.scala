package test

import java.net.NetworkInterface

import scala.jdk.CollectionConverters._
object TestListNeworkInterface extends App {

  private def listInterfaces(): Unit =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(_.isSiteLocalAddress)
      .map(_.getHostAddress)
      .foreach(addr => println(addr))

  private def bestInterface(): Option[String] =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(_.isSiteLocalAddress)
      .map(_.getHostAddress)
      //.getOrElse("localhost")
  println(bestInterface())
  //listInterfaces()
}
