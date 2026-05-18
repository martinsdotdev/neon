package neon.app.http

import neon.app.ServiceRegistry
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.{ExecutionContext, Future}

import scala.language.implicitConversions

/** Binds the HTTP route tree and starts the Pekko HTTP server. */
object HttpServer:

  def routes(
      registry: ServiceRegistry,
      secureCookies: Boolean = true
  )(using ExecutionContext, ActorSystem[?]): Route =
    handleExceptions(ProblemRouteHandlers.exceptionHandler):
      handleRejections(ProblemRouteHandlers.rejectionHandler):
        RequestLoggingDirective.withRequestLogging:
          concat(
            AuthRoutes(registry.authenticationService, secureCookies),
            TaskRoutes(
              registry.taskCompletionService,
              registry.taskLifecycleService,
              registry.authenticationService
            ),
            MobileTaskRoutes(
              registry.taskRepository,
              registry.taskLifecycleService,
              registry.authenticationService
            ),
            MobileLookupRoutes(
              registry.skuRepository,
              registry.locationRepository,
              registry.handlingUnitRepository,
              registry.authenticationService
            ),
            WaveRoutes(
              registry.waveCancellationService,
              registry.wavePlanningService,
              registry.orderRepository,
              registry.authenticationService
            ),
            TransportOrderRoutes(
              registry.transportOrderConfirmationService,
              registry.transportOrderCancellationService,
              registry.authenticationService
            ),
            ConsolidationGroupRoutes(
              registry.consolidationGroupCompletionService,
              registry.consolidationGroupCancellationService,
              registry.authenticationService
            ),
            WorkstationRoutes(
              registry.workstationAssignmentService,
              registry.workstationLifecycleService,
              registry.authenticationService
            ),
            HandlingUnitRoutes(
              registry.handlingUnitLifecycleService,
              registry.authenticationService
            ),
            SlotRoutes(
              registry.slotService,
              registry.authenticationService
            ),
            InventoryRoutes(
              registry.inventoryService,
              registry.authenticationService
            ),
            StockPositionRoutes(
              registry.stockPositionService,
              registry.authenticationService
            ),
            InboundRoutes(
              registry.inboundDeliveryService,
              registry.authenticationService
            ),
            CycleCountRoutes(
              registry.cycleCountService,
              registry.authenticationService
            ),
            NotificationRoutes(registry.authenticationService)
          )

  def start(
      registry: ServiceRegistry,
      systemRef: ActorSystem[?]
  )(using ExecutionContext): Future[Http.ServerBinding] =
    given ActorSystem[?] = systemRef
    val system = systemRef
    val httpConfig = system.settings.config.getConfig("neon.http")
    val host = httpConfig.getString("host")
    val port = httpConfig.getInt("port")
    val secureCookies =
      system.settings.config.getBoolean("neon.auth.secure-cookies")

    Http()
      .newServerAt(host, port)
      .bind(routes(registry, secureCookies))
      .map { binding =>
        system.log.info(
          s"Neon WES HTTP server bound to ${binding.localAddress}"
        )
        binding
      }
