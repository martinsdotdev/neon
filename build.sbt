ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

val munitVersion       = "1.1.0"
val uuidCreatorVersion = "6.1.1"

lazy val wave = project
  .in(file("wave"))
  .settings(
    name := "neon-wave",
    libraryDependencies ++= Seq(
      "com.github.f4b6a3" % "uuid-creator" % uuidCreatorVersion,
      "org.scalameta"    %% "munit"         % munitVersion % Test
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(wave)
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
