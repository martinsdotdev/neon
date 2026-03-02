ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

val scalatestVersion   = "3.2.19"
val uuidCreatorVersion = "6.1.1"

ThisBuild / libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % Test

lazy val common = project
  .in(file("common"))
  .settings(
    name := "neon-common",
    libraryDependencies ++= Seq(
      "com.github.f4b6a3" % "uuid-creator" % uuidCreatorVersion
    )
  )

lazy val order = project
  .in(file("order"))
  .dependsOn(common)
  .settings(name := "neon-order")

lazy val wave = project
  .in(file("wave"))
  .dependsOn(common, order)
  .settings(name := "neon-wave")

lazy val task = project
  .in(file("task"))
  .dependsOn(common)
  .settings(name := "neon-task")

lazy val consolidation = project
  .in(file("consolidation"))
  .dependsOn(common)
  .settings(name := "neon-consolidation")

lazy val app = project
  .in(file("app"))
  .dependsOn(common, wave, task, consolidation)
  .settings(name := "neon-app")

lazy val root = project
  .in(file("."))
  .aggregate(common, order, wave, task, consolidation, app)
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
