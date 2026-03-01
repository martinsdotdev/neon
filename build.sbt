ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

val munitVersion       = "1.1.0"
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
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val picking = project
  .in(file("picking"))
  .dependsOn(common)
  .settings(
    name := "neon-picking",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val consolidation = project
  .in(file("consolidation"))
  .dependsOn(common)
  .settings(
    name := "neon-consolidation",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(common, wave, picking, consolidation)
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
