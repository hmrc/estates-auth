import sbt.*

object AppDependencies {

  val bootstrapVersion = "7.23.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion,
    "org.scalatest"           %% "scalatest"                  % "3.2.17",
    "org.wiremock"            %  "wiremock-standalone"        % "3.3.1",
    "org.mockito"             %  "mockito-core"               % "5.8.0",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
