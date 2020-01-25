addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = (project in file("."))
  .settings(
    name := "interop-googleapis"
  )
  .settings(
    scalaVersion := "2.13.1",
    version := "0.1.0-SNAPSHOT"
  )
  .settings(
    libraryDependencies ++= Dependencies.compile ++ Dependencies.test
  )
  .settings(
    scalafmtOnCompile := true,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos",
      "-Ywarn-unused:imports"
    )
  )
