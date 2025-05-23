import sbt.*

object AppDependencies {

  val bootstrapVersion = "9.12.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,

  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}
