import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt.{ModuleID, _}

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-play-26" % "1.14.0",
    "com.typesafe.play"       %% "play-json-joda"    % "2.7.4"
  )

  val test = Seq(
    "org.scalatest"           %% "scalatest"                % "3.0.8"                 % "test",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.2"                 % "test, it",
    "com.github.tomakehurst"  %  "wiremock-standalone"      % "2.17.0"                % "test, it",
    "org.mockito"             %  "mockito-all"              % "1.10.19"               % "test, it"
  )

  val akkaVersion = "2.5.23"
  val akkaHttpVersion = "10.0.15"

  val overrides = Seq(
    "com.typesafe.akka" %% "akka-stream_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core_2.12" % akkaHttpVersion
  )


}
