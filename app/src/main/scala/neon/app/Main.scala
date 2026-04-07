package neon.app

import org.apache.pekko.actor.typed.ActorSystem

/** Neon WES application entry point. Creates the actor system with the [[Guardian]] root actor which
  * bootstraps all infrastructure.
  */
@main def main(): Unit =
  ActorSystem[Nothing](Guardian(), "neon-wes")
