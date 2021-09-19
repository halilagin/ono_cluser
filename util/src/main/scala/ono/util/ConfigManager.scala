package ono.util

import com.typesafe.config.{Config, ConfigFactory}


/**
 * check command line argument conf_from=cmd
 * if cmd present read commandline name=value pairs and ovewrite to classpath:application.conf.
 * if not, just parse classpath:application.conf
 */
object ConfigManager {


  def read(args:Array[String]): Config ={
    ConfigFactory.invalidateCaches()
    //call ConfigFactory.load() for dynamic load of application.conf with -Dconfig.file=
    var conf:Config  = ConfigFactory.load();

    def overrideConfigWithCmdArgs(): Unit = {
      args.filter(_.contains("conf_from")==false).foreach { c =>
        val myConfig = ConfigFactory.parseString(c)
        println("changing conf", c)
        conf = myConfig.withFallback(conf)

      }
    }

    // setup config with applicaiton.conf or with command line arguments
    args.filter(x => x.contains("conf_from=")).headOption match {
      case Some(arg) => arg.split("=")(1) match {
        case "cmd" => overrideConfigWithCmdArgs()
        case _ => //use default application.conf
      }
      case None => ; //"no command arg passed"
    }
    // conf variable is set now.
    conf
  }
}