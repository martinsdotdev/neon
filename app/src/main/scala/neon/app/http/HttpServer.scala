package neon.app.http

import neon.app.ServiceRegistry
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.Future

/** Binds the HTTP route tree and starts the Pekko HTTP server. */
object HttpServer:

  def routes(registry: ServiceRegistry): Route =
    concat(
      TaskRoutes(registry.taskCompletionService),
      WaveRoutes(registry.waveCancellationService),
      TransportOrderRoutes(
        registry.transportOrderConfirmationService
      ),
      ConsolidationGroupRoutes(
        registry.consolidationGroupCompletionService
      ),
      WorkstationRoutes(registry.workstationAssignmentService)
    )

  def start(
      registry: ServiceRegistry,
      system: ActorSystem[?]
  ): Future[Http.ServerBinding] =
    given ActorSystem[?] = system
    val config = system.settings.config.getConfig("neon.http")
    val host = config.getString("host")
    val port = config.getInt("port")

    Http()
      .newServerAt(host, port)
      .bind(routes(registry))
      .map { binding =>
        system.log.info(
          s"Neon WES HTTP server bound to ${binding.localAddress}"
        )
        binding
      }(using system.executionContext)
