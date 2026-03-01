ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

val scalatestVersion   = "3.2.19"
val uuidCreatorVersion = "6.1.1"

lazy val common = project
  .in(file("common"))
  .settings(
    name := "neon-common",
    libraryDependencies ++= Seq(
      "com.github.f4b6a3" % "uuid-creator" % uuidCreatorVersion
    )
  )

lazy val wave = project
  .in(file("wave"))
  .dependsOn(common)
  .settings(
    name := "neon-wave",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    )
  )

lazy val task = project
  .in(file("task"))
  .dependsOn(common)
  .settings(
    name := "neon-task",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    )
  )

lazy val consolidation = project
  .in(file("consolidation"))
  .dependsOn(common)
  .settings(
    name := "neon-consolidation",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    )
  )

lazy val app = project
  .in(file("app"))
  .dependsOn(common, wave, task)
  .settings(
    name := "neon-app",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(common, wave, task, consolidation, app)
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
