package neon.app

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.*

/** Root actor that bootstraps the Neon WES application: initializes cluster sharding for all
  * aggregate actors (via repository constructors), wires async services, and starts the HTTP
  * server.
  */
object Guardian:

  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      given ActorSystem[Nothing] = context.system
      given Timeout = 5.seconds

      val registry = ServiceRegistry(context.system)

      context.log.info("Neon WES Guardian started")

      // HTTP server would be started here once routes are wired
      // HttpServer.start(registry, context.system)

      Behaviors.empty
    }
