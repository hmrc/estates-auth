import sbt.*

object AppDependencies {

  val bootstrapVersion = "7.19.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "com.typesafe.play"       %% "play-json-joda"             % "2.9.4"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "org.scalatest"           %% "scalatest"                  % "3.2.16",
    "com.github.tomakehurst"  %  "wiremock-standalone"        % "2.27.2",
    "org.mockito"             %  "mockito-core"               % "5.4.0",
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.64.8"
  ).map(_ % Test)

}
