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
  .dependsOn(common, order, sku)
  .settings(name := "neon-wave")

lazy val task = project
  .in(file("task"))
  .dependsOn(common)
  .settings(name := "neon-task")

lazy val consolidationGroup = project
  .in(file("consolidation-group"))
  .dependsOn(common)
  .settings(name := "neon-consolidation-group")

lazy val handlingUnit = project
  .in(file("handling-unit"))
  .dependsOn(common)
  .settings(name := "neon-handling-unit")

lazy val transportOrder = project
  .in(file("transport-order"))
  .dependsOn(common)
  .settings(name := "neon-transport-order")

lazy val location = project
  .in(file("location"))
  .dependsOn(common)
  .settings(name := "neon-location")

lazy val inventory = project
  .in(file("inventory"))
  .dependsOn(common)
  .settings(name := "neon-inventory")

lazy val sku = project
  .in(file("sku"))
  .dependsOn(common)
  .settings(name := "neon-sku")

lazy val user = project
  .in(file("user"))
  .dependsOn(common)
  .settings(name := "neon-user")

lazy val carrier = project
  .in(file("carrier"))
  .dependsOn(common)
  .settings(name := "neon-carrier")

lazy val workstation = project
  .in(file("workstation"))
  .dependsOn(common)
  .settings(name := "neon-workstation")

lazy val slot = project
  .in(file("slot"))
  .dependsOn(common)
  .settings(name := "neon-slot")

lazy val core = project
  .in(file("core"))
  .dependsOn(
    common,
    wave,
    task,
    consolidationGroup,
    handlingUnit,
    transportOrder,
    workstation,
    slot,
    location,
    carrier
  )
  .settings(name := "neon-core")

lazy val root = project
  .in(file("."))
  .aggregate(
    common,
    order,
    wave,
    task,
    location,
    inventory,
    consolidationGroup,
    handlingUnit,
    transportOrder,
    sku,
    user,
    carrier,
    workstation,
    slot,
    core
  )
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
