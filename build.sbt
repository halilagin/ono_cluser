/* vim: set ts=2 sts=2 sw=2 noet */

name := "ono_cluster"
organization in ThisBuild := "peralabs"
//don't use scala 2.12.0, because it throws error of "Task not serializable" while applying groupby function on streaming data.
scalaVersion in ThisBuild := "2.13.1"
//cancelable in Global := false
//fork in run := true
//connectInput in run := true

version := "0.0.1"

lazy val dependencies =
	new {
		val akkaV							= "2.6.15"
    val akkaHTTPV         = "10.2.5"
    val logbackV					= "1.2.3"
		val scalatestV				= "3.2.1"
		val slf4jV						= "1.7.30"
		val jacksonV					= "2.11.1"
		val yamlV							= "1.27"

    val logback                 = "ch.qos.logback"                      %       "logback-classic"               % logbackV
    val slf4j                   = "org.slf4j"                           %       "slf4j-api"                     % slf4jV
    val akkaActor               = "com.typesafe.akka"                   %%      "akka-actor"                    % akkaV
    val akkaStream              = "com.typesafe.akka"                   %%      "akka-stream"                   % akkaV
    val akkaStreamTestKit       = "com.typesafe.akka"                   %%      "akka-stream-testkit"           % akkaV
    val akkaHTTP                = "com.typesafe.akka"                   %%       "akka-http"                    % akkaHTTPV
    val akkaHttpSprayJson       = "com.typesafe.akka"                   %%       "akka-http-spray-json"         % akkaHTTPV
    val akkaHttpTestKit         = "com.typesafe.akka"                   %%       "akka-http-spray-json"         % akkaHTTPV

    val akkaSlf4j               = "com.typesafe.akka"                   %%      "akka-slf4j"                    % akkaV
    val scalatest               = "org.scalatest"                       %%      "scalatest"                     % scalatestV
    val jackson                 = "com.fasterxml.jackson.core"          %       "jackson-core"                  % jacksonV
    val jacksonDataformatYaml   = "com.fasterxml.jackson.dataformat"    %       "jackson-dataformat-yaml"       % jacksonV
    val yaml                    = "org.yaml"                            %       "snakeyaml"                     % yamlV
    // https://mvnrepository.com/artifact/com.typesafe.akka/akka-stream

  }

val modelDependencies = Seq()
val daoDependencies = Seq()
val serviceDependencies = Seq()
val webDependencies = Seq()
val akkaTrainingDependencies = Seq(
  dependencies.akkaActor,
  dependencies.akkaStream,
  dependencies.akkaHTTP,
  dependencies.akkaHttpSprayJson,
  dependencies.akkaHttpTestKit,
  dependencies.akkaSlf4j,
  dependencies.jackson,
  dependencies.logback,
  dependencies.scalatest,
  dependencies.akkaStreamTestKit,
  dependencies.yaml
)

val onoClusterMasterDependencies = Seq(
  dependencies.akkaActor,
  dependencies.akkaStream,
  dependencies.akkaHTTP,
  dependencies.akkaHttpSprayJson,
  dependencies.akkaHttpTestKit,
  dependencies.akkaSlf4j,
  dependencies.jackson,
  dependencies.logback,
  dependencies.scalatest,
  dependencies.akkaStreamTestKit,
  dependencies.yaml
)



resolvers ++= Seq (
  Opts.resolver.sbtIvySnapshots,
  "Local Ivy2 Repository" at "file://" + Path.userHome.absolutePath + "/.ivy2/local",
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  "Confluent" at "https://packages.confluent.io/maven"
)

resolvers += Resolver.mavenLocal


lazy val commonSettings = Seq(
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.ivy2/local",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.mavenLocal,
    "Confluent" at "https://packages.confluent.io/maven"
  )
)

lazy val settings = commonSettings 


lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)



lazy val modelAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)


lazy val daoAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)


lazy val serviceAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)


lazy val webAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)



lazy val akkaAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val onoClusterMasterAssemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case "application.conf"            => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)


lazy val global = project
  .in(file("."))
  .settings(settings)
  .disablePlugins(AssemblyPlugin)
  .aggregate(
//    model,
//    dao,
//    service,
//    web
    //akka_http,
    //akka_streams,
    ono_cluster_master
  )



val copyJars = TaskKey[Unit]("copyJars", "Copy all dependency jars to target/lib")
copyJars := {
	val files: Seq[File] = (fullClasspath in Compile).value.files.filter( !_.isDirectory)
	files.foreach( f => IO.copyFile(f, file("./target/lib/" + f.getName())))
}


lazy val model = project
  .in(file("model"))
  .settings(
    name := "model",
    settings,
		modelAssemblySettings,
    libraryDependencies ++= modelDependencies,

  )
  //.disablePlugins(AssemblyPlugin)


lazy val dao = project
  .in(file("dao"))
  .settings(
    name := "dao",
    settings,
		daoAssemblySettings,
    libraryDependencies ++= daoDependencies,
  )
  //.disablePlugins(AssemblyPlugin)
	.dependsOn(model)


lazy val service = project
  .in(file("service"))
  .settings(
    name := "service",
    settings,
		serviceAssemblySettings,
    libraryDependencies ++= serviceDependencies,
  )
  //.disablePlugins(AssemblyPlugin)
	.dependsOn(dao)


lazy val web = project
  .in(file("web"))
  .settings(
    name := "web",
    settings,
		webAssemblySettings,
    libraryDependencies ++= webDependencies,
  )
  //.disablePlugins(AssemblyPlugin)
	.dependsOn(service)

lazy val akka_http = project
  .in(file("akka_http"))
  .settings(
    name := "akka_http",
    settings,
    akkaAssemblySettings,
    libraryDependencies ++= akkaTrainingDependencies,
  )
  //.disablePlugins(AssemblyPlugin)
  //.dependsOn(service)
lazy val ono_cluster_master = project
  .in(file("ono_cluster_master"))
  .settings(
    name := "ono_cluster_master",
    settings,
    onoClusterMasterAssemblySettings,
    libraryDependencies ++= onoClusterMasterDependencies,
  )
  //.disablePlugins(AssemblyPlugin)
  //.dependsOn(service)



lazy val akka_streams = project
  .in(file("akka_streams"))
  .settings(
    name := "akka_streams",
    settings,
    akkaAssemblySettings,
    libraryDependencies ++= akkaTrainingDependencies,
  )
