import play.sbt.PlayImport.PlayKeys


ThisBuild / scalaVersion := "2.13.16"
ThisBuild / majorVersion := 0

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings())
  .settings(
    name := "estates-auth",
    libraryDependencies ++= AppDependencies(),
    PlayKeys.playDefaultPort := 8836,
    scalacOptions ++= Seq("-feature", "-Wconf:src=routes/.*:s", "-Wconf:cat=unused-imports&src=routes/.*:s")
  )

val excludedPackages = Seq(
  "<empty>",
  ".*Reverse.*",
  ".*Routes.*",
  ".*standardError*.*",
  ".*main_template*.*",
  "uk.gov.hmrc.BuildInfo",
  "app.*",
  "prod.*",
  "config.*",
  "testOnlyDoNotUseInAppConf.*",
  "views.html.*",
  "testOnly.*"
)
