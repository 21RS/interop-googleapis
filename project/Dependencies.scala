import sbt._

object Dependencies {

  // Compile Time Dependencies
  lazy val zio       = "dev.zio"        %% "zio"       % "1.0.0-RC14"
  lazy val googleapi = "com.google.api" % "api-common" % "1.8.1"

  // Test Dependencies
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0"

  val compile: Seq[ModuleID] = Seq(
    zio,
    googleapi
  )

  val test: Seq[ModuleID] = Seq(
    scalaTest % "test"
  )
}
