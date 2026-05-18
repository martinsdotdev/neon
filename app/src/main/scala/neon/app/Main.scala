package neon.app

import org.apache.pekko.actor.typed.ActorSystem

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Neon WES application entry point. Creates the actor system with the [[Guardian]] root actor
  * which bootstraps all infrastructure. Blocks on system termination so the JVM stays alive and
  * sbt's `run` task doesn't race shutdown hooks against the actor system's lazy initialization
  * (notably the r2dbc-postgresql connection factory's Netty resolver group).
  */
@main def main(): Unit =
  val system = ActorSystem[Nothing](Guardian(), "neon-wes")
  Await.result(system.whenTerminated, Duration.Inf)
