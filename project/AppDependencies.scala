import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt.{ModuleID, _}

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "5.8.0",
    "com.typesafe.play"       %% "play-json-joda"             % "2.7.4"
  )

  val test = Seq(
    "org.scalatest"           %% "scalatest"                % "3.2.7"                 % "test",
    "com.typesafe.play"       %% "play-test"                % current                 % "test",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"                 % "test",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "5.1.0"                 % "test",
    "org.scalatestplus"       %% "scalatestplus-mockito"    % "1.0.0-M2"              % "test",
    "com.github.tomakehurst"  %  "wiremock-standalone"      % "2.27.2"                % "test",
    "org.mockito"             %  "mockito-all"              % "1.10.19"               % "test",
    "com.vladsch.flexmark"    % "flexmark-all"              % "0.35.10"                % "test"
  )

  val akkaVersion = "2.6.7"
  val akkaHttpVersion = "10.1.12"

  val overrides = Seq(
    "com.typesafe.akka" %% "akka-stream_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor_2.12" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core_2.12" % akkaHttpVersion
  )
}
