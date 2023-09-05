import sbt.*

object AppDependencies {

  val bootstrapVersion = "7.21.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "com.typesafe.play"       %% "play-json-joda"             % "2.9.4"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "org.scalatest"           %% "scalatest"                  % "3.2.16",
    "org.wiremock"            %  "wiremock-standalone"        % "3.0.1",
    "org.mockito"             %  "mockito-core"               % "5.5.0",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
