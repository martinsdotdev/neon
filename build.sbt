ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

lazy val root = project
  .in(file("."))
  .settings(
    name := "neon-wes",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := "3.8.2",

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )
