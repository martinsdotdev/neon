ThisBuild / scalaVersion         := "3.8.2"
ThisBuild / version              := "0.1.0-SNAPSHOT"
ThisBuild / organization         := "com.mais.neon"
ThisBuild / semanticdbEnabled    := true
ThisBuild / semanticdbVersion    := scalafixSemanticdb.revision
ThisBuild / scalacOptions       ++= Seq("-Wunused:imports")

val scalatestVersion   = "3.2.19"
val uuidCreatorVersion = "6.1.1"

val pekkoVersion                 = "1.2.0"
val pekkoHttpVersion             = "1.1.0"
val pekkoPersistenceR2dbcVersion = "1.1.0"
val pekkoProjectionVersion       = "1.1.0"
val circeVersion                 = "0.14.13"
val logbackVersion               = "1.5.18"

ThisBuild / libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % Test

// Shared Pekko dependencies for event-sourced aggregate modules
val pekkoActorDependencies = Seq(
  "org.apache.pekko" %% "pekko-actor-typed"            % pekkoVersion,
  "org.apache.pekko" %% "pekko-cluster-sharding-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-persistence-typed"      % pekkoVersion,
  "org.apache.pekko" %% "pekko-serialization-jackson"  % pekkoVersion,
  "org.apache.pekko" %% "pekko-actor-testkit-typed"    % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-persistence-testkit"    % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-stream-testkit"         % pekkoVersion % Test
)

lazy val common = project
  .in(file("common"))
  .settings(
    name := "neon-common",
    libraryDependencies ++= Seq(
      "com.github.f4b6a3" % "uuid-creator" % uuidCreatorVersion
    )
  )

// --- Reference data modules (no Pekko, read-only) ---

lazy val order = project
  .in(file("order"))
  .dependsOn(common)
  .settings(name := "neon-order")

lazy val location = project
  .in(file("location"))
  .dependsOn(common)
  .settings(name := "neon-location")

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

// --- Event-sourced aggregate modules (with Pekko actors) ---

lazy val wave = project
  .in(file("wave"))
  .dependsOn(common, order, sku)
  .settings(
    name := "neon-wave",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val task = project
  .in(file("task"))
  .dependsOn(common)
  .settings(
    name := "neon-task",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val consolidationGroup = project
  .in(file("consolidation-group"))
  .dependsOn(common)
  .settings(
    name := "neon-consolidation-group",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val handlingUnit = project
  .in(file("handling-unit"))
  .dependsOn(common)
  .settings(
    name := "neon-handling-unit",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val transportOrder = project
  .in(file("transport-order"))
  .dependsOn(common)
  .settings(
    name := "neon-transport-order",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val workstation = project
  .in(file("workstation"))
  .dependsOn(common)
  .settings(
    name := "neon-workstation",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val slot = project
  .in(file("slot"))
  .dependsOn(common)
  .settings(
    name := "neon-slot",
    libraryDependencies ++= pekkoActorDependencies
  )

lazy val inventory = project
  .in(file("inventory"))
  .dependsOn(common)
  .settings(
    name := "neon-inventory",
    libraryDependencies ++= pekkoActorDependencies
  )

// --- Orchestration module ---

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

// --- Application module (routes, bootstrap) ---

lazy val app = project
  .in(file("app"))
  .dependsOn(core, inventory, order, sku, user)
  .settings(
    name := "neon-app",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http"                    % pekkoHttpVersion,
      "org.apache.pekko" %% "pekko-cluster-typed"           % pekkoVersion,
      "org.apache.pekko" %% "pekko-persistence-r2dbc"       % pekkoPersistenceR2dbcVersion,
      "org.apache.pekko" %% "pekko-projection-r2dbc"        % pekkoProjectionVersion,
      "org.apache.pekko" %% "pekko-projection-eventsourced" % pekkoProjectionVersion,
      "ch.qos.logback"    % "logback-classic"               % logbackVersion,
      "io.circe"         %% "circe-core"                    % circeVersion,
      "io.circe"         %% "circe-generic"                 % circeVersion,
      "io.circe"         %% "circe-parser"                  % circeVersion,
      "org.apache.pekko" %% "pekko-http-testkit"            % pekkoHttpVersion % Test,
      "org.apache.pekko" %% "pekko-projection-testkit"      % pekkoProjectionVersion % Test
    )
  )

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
    core,
    app
  )
  .settings(
    name := "neon-wes",
    publish / skip := true
  )
