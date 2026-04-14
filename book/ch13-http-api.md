# The HTTP API

We have built a write side (event-sourced actors), a read side (CQRS
projections), and a service layer that orchestrates domain logic. Now we
need a way for the outside world to talk to our system. In this chapter we
will build the HTTP layer that exposes Neon WES to warehouse operators, RF
scanners, and the React frontend.

The HTTP layer in Neon WES is intentionally thin. Routes do not contain
business logic. They parse requests, call async services, map results to HTTP
responses, and nothing more. This keeps the domain logic testable in
isolation (as we saw in Chapters 6 and 7) while giving us a clean, RESTful
interface.


## Route Architecture

Every domain area in Neon WES has its own route object. `HttpServer` wires
them all together into a single route tree.

```scala
object HttpServer:

  def routes(
      registry: ServiceRegistry,
      secureCookies: Boolean = true
  )(using ExecutionContext): Route =
    RequestLoggingDirective.withRequestLogging:
      concat(
        AuthRoutes(registry.authenticationService, secureCookies),
        TaskRoutes(
          registry.taskCompletionService,
          registry.taskLifecycleService,
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
        )
      )
```

<small>*File: app/src/main/scala/neon/app/http/HttpServer.scala*</small>

Several things are worth noting here:

**One route object per domain area.** `TaskRoutes`, `WaveRoutes`,
`WorkstationRoutes`, and so on each live in their own file. This keeps
individual route files small and focused.

**ServiceRegistry injection.** The `ServiceRegistry` (created by `Guardian`
at startup) holds references to all async services and repositories. Each
route object receives only the services it needs.

**RequestLoggingDirective wraps everything.** Every request passes through a
logging directive that emits a structured access log line with method, path,
status code, duration, and trace ID. We will look at this in more detail
later.

**Server binding.** The `start` method reads host and port from configuration
and binds the route tree:

```scala
def start(
    registry: ServiceRegistry,
    system: ActorSystem[?]
)(using ExecutionContext): Future[Http.ServerBinding] =
  given ActorSystem[?] = system
  val httpConfig = system.settings.config.getConfig("neon.http")
  val host = httpConfig.getString("host")
  val port = httpConfig.getInt("port")
  val secureCookies =
    system.settings.config.getBoolean("neon.auth.secure-cookies")

  Http()
    .newServerAt(host, port)
    .bind(routes(registry, secureCookies))
```

<small>*File: app/src/main/scala/neon/app/http/HttpServer.scala*</small>


## Circe JSON Marshalling

Neon WES uses circe for JSON serialization, integrated with Pekko HTTP
through a small `CirceSupport` object. This gives us automatic marshalling
and unmarshalling for any type that has a circe `Encoder` or `Decoder`.

```scala
object CirceSupport:

  private val printer: Printer =
    Printer.noSpaces.copy(dropNullValues = true)

  given [A: Encoder]: ToEntityMarshaller[A] =
    Marshaller.withFixedContentType(
      ContentTypes.`application/json`
    ) { a =>
      HttpEntity(
        ContentTypes.`application/json`,
        printer.print(a.asJson)
      )
    }

  given [A: Decoder]: FromEntityUnmarshaller[A] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map { str =>
        decode[A](str) match
          case Right(value) => value
          case Left(error)  => throw error
      }
```

<small>*File: app/src/main/scala/neon/app/http/CirceSupport.scala*</small>

Let's unpack this:

**The printer drops null values.** `Printer.noSpaces.copy(dropNullValues =
true)` means optional fields that are `None` simply disappear from the JSON
output rather than appearing as `"field": null`. This produces cleaner
responses.

**Generic givens.** The `given [A: Encoder]` syntax creates a
`ToEntityMarshaller[A]` for *any* type that has an `Encoder` instance. When
a route calls `complete(someResponse)`, the compiler finds the marshaller
through implicit resolution. No registration step is needed.

**Decoder error propagation.** If the JSON body cannot be decoded, the
unmarshaller throws the circe error, which Pekko HTTP catches and returns as
a 400 Bad Request.

In the route objects, DTO case classes derive their codecs with a single
annotation:

```scala
case class PlanAndReleaseRequest(
    orderIds: List[String],
    grouping: String,
    dockAssignments: List[DockAssignmentDto]
) derives Decoder

case class WaveReleaseResponse(
    status: String,
    waveId: String,
    tasksCreated: Int,
    consolidationGroupsCreated: Int
) derives Encoder.AsObject
```

<small>*File: app/src/main/scala/neon/app/http/WaveRoutes.scala*</small>

`derives Decoder` generates a `Decoder` instance at compile time using
Scala 3's typeclass derivation. `derives Encoder.AsObject` does the same for
encoding, producing a JSON object (as opposed to a primitive value).

> **Note:** DTOs are deliberately separate from domain types. The
> `PlanAndReleaseRequest` uses `List[String]` for order IDs, not
> `List[OrderId]`. The route is responsible for parsing strings into opaque
> type IDs. This keeps the JSON contract stable even if internal ID
> representations change.


## Walkthrough: WaveRoutes

Let's trace through `WaveRoutes` to see how a request becomes a response.
This route object handles two operations: planning and releasing a wave (POST)
and cancelling a wave (DELETE).

### The Plan-and-Release Endpoint

```scala
object WaveRoutes:

  def apply(
      waveCancellationService: AsyncWaveCancellationService,
      wavePlanningService: AsyncWavePlanningService,
      orderRepository: AsyncOrderRepository,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("waves"):
      concat(
        path("plan-and-release"):
          AuthDirectives.requirePermission(
            Permission.WavePlan, authService
          ): _ =>
            post:
              entity(as[PlanAndReleaseRequest]): request =>
                val orderIds = request.orderIds
                  .map(s => OrderId(UUID.fromString(s)))
                val grouping =
                  OrderGrouping.valueOf(request.grouping)
                val dockAssignments =
                  request.dockAssignments.map { dto =>
                    DockCarrierAssignment(
                      dockId = LocationId(UUID.fromString(dto.dockId)),
                      carrierId = CarrierId(UUID.fromString(dto.carrierId))
                    )
                  }
                onSuccess(
                  orderRepository
                    .findByIds(orderIds)
                    .flatMap { orders =>
                      wavePlanningService.planAndRelease(
                        orders, grouping, dockAssignments,
                        Instant.now()
                      )
                    }
                ):
                  case Right(result) =>
                    complete(
                      WaveReleaseResponse(
                        status = "released",
                        waveId = result.wavePlan.wave.id.value.toString,
                        tasksCreated = result.release.tasks.size,
                        consolidationGroupsCreated =
                          result.release.consolidationGroups.size
                      )
                    )
                  case Left(_: WavePlanningError.DockConflict) =>
                    complete(StatusCodes.Conflict)
                  case Left(_) =>
                    complete(StatusCodes.UnprocessableEntity)
```

<small>*File: app/src/main/scala/neon/app/http/WaveRoutes.scala*</small>

Here is the flow, step by step:

1. **Path matching**: `pathPrefix("waves")` and `path("plan-and-release")`
   match the URL `/waves/plan-and-release`.

2. **Authorization**: `AuthDirectives.requirePermission(Permission.WavePlan,
   authService)` validates the session cookie and checks that the user has
   the `WavePlan` permission. If not, it short-circuits with 401 or 403.

3. **Request parsing**: `entity(as[PlanAndReleaseRequest])` uses the circe
   unmarshaller to deserialize the JSON body.

4. **DTO to domain conversion**: The route converts string IDs to opaque type
   IDs (`OrderId`, `LocationId`, `CarrierId`) and the grouping string to an
   `OrderGrouping` enum.

5. **Service call**: The route fetches orders from the repository, then calls
   `wavePlanningService.planAndRelease`. Both operations return `Future`
   values, composed with `flatMap`.

6. **Result mapping**: `onSuccess` unwraps the `Future`. The service returns
   `Either[WavePlanningError, WavePlanningResult]`. We pattern match on the
   `Either` to produce the appropriate HTTP response.

### The Cancel Endpoint

The cancel endpoint follows the same structure with a different HTTP method
and path pattern:

```scala
        path(Segment): waveIdStr =>
          AuthDirectives.requirePermission(
            Permission.WaveCancel, authService
          ): _ =>
            delete:
              val waveId = WaveId(UUID.fromString(waveIdStr))
              onSuccess(
                waveCancellationService.cancel(waveId, Instant.now())
              ):
                case Right(result) =>
                  complete(
                    WaveCancellationResponse(
                      status = "cancelled",
                      waveId = result.cancelled.id.value.toString,
                      cancelledTasks = result.cancelledTasks.size,
                      cancelledTransportOrders =
                        result.cancelledTransportOrders.size,
                      cancelledConsolidationGroups =
                        result.cancelledConsolidationGroups.size
                    )
                  )
                case Left(_: WaveCancellationError.WaveNotFound) =>
                  complete(StatusCodes.NotFound)
                case Left(_: WaveCancellationError.WaveAlreadyTerminal) =>
                  complete(StatusCodes.Conflict)
```

<small>*File: app/src/main/scala/neon/app/http/WaveRoutes.scala*</small>

`path(Segment)` captures the wave ID from the URL path. `DELETE
/waves/abc-123-def` extracts `"abc-123-def"` as `waveIdStr`. The rest of the
pattern is identical: authorize, call service, match on `Either`, return
response.


## Error Mapping

Neon WES uses sealed trait ADTs for errors (as we saw in Chapter 7). The HTTP
layer maps these domain errors to standard HTTP status codes. Here is the
mapping convention used across all route objects:

| Domain Error Pattern | HTTP Status | When |
|---|---|---|
| `XxxNotFound` | 404 Not Found | Entity does not exist |
| `XxxAlreadyTerminal`, `XxxInWrongState` | 409 Conflict | State machine violation |
| `InvalidXxx`, validation errors | 422 Unprocessable Entity | Valid request structure, invalid data |
| `VerificationRequired` | 428 Precondition Required | Business precondition not met |

Let's look at how `TaskRoutes` maps the `TaskCompletionError` ADT:

```scala
case Left(error) =>
  error match
    case _: TaskCompletionError.TaskNotFound =>
      complete(StatusCodes.NotFound)
    case _: TaskCompletionError.TaskNotAssigned =>
      complete(StatusCodes.Conflict)
    case _: TaskCompletionError.InvalidActualQuantity =>
      complete(StatusCodes.UnprocessableEntity)
    case _: TaskCompletionError.VerificationRequired =>
      complete(StatusCodes.PreconditionRequired)
```

<small>*File: app/src/main/scala/neon/app/http/TaskRoutes.scala*</small>

And `TaskRoutes` also factors out a reusable error mapper for lifecycle
errors:

```scala
private def mapLifecycleError(error: TaskLifecycleError): Route =
  error match
    case _: TaskLifecycleError.TaskNotFound =>
      complete(StatusCodes.NotFound)
    case _: TaskLifecycleError.TaskInWrongState =>
      complete(StatusCodes.Conflict)
    case _: TaskLifecycleError.TaskAlreadyTerminal =>
      complete(StatusCodes.Conflict)
    case _: TaskLifecycleError.UserNotFound =>
      complete(StatusCodes.UnprocessableEntity)
    case _: TaskLifecycleError.UserNotActive =>
      complete(StatusCodes.UnprocessableEntity)
```

<small>*File: app/src/main/scala/neon/app/http/TaskRoutes.scala*</small>

This approach has an important benefit: the compiler guarantees exhaustive
matching. If someone adds a new error case to the sealed trait, the route
file will produce a warning until it handles the new case. No error can
silently slip through.

> **Note:** The routes return status codes without error bodies in most cases.
> In a production system you might add a JSON error body with a machine-readable
> error code and a human-readable message. The important thing is that the
> domain error ADT is the single source of truth for what can go wrong.


## Authentication and Authorization

Every route in Neon WES (except the auth routes themselves) is protected by
session-based authentication with role-based access control (RBAC).

### Session Cookies

The `AuthRoutes` object handles login and logout. On successful login, it
sets an HTTP-only session cookie:

```scala
path("login"):
  post:
    entity(as[LoginRequest]): request =>
      extractClientIP: clientIp =>
        optionalHeaderValueByName("User-Agent"): userAgent =>
          onSuccess(
            authService.login(
              request.login, request.password,
              Some(clientIp.toOption
                .map(_.getHostAddress)
                .getOrElse("unknown")),
              userAgent
            )
          ):
            case Right((token, context)) =>
              setCookie(sessionCookie(token, secureCookies)):
                complete(AuthResponse.fromContext(context))
            case Left(AuthError.InvalidCredentials) =>
              complete(StatusCodes.Unauthorized)
            case Left(AuthError.AccountInactive) =>
              complete(StatusCodes.Forbidden)
```

<small>*File: app/src/main/scala/neon/app/http/AuthRoutes.scala*</small>

The session cookie is configured with security best practices: `httpOnly =
true` (not accessible from JavaScript), `secure = true` in production (HTTPS
only), `SameSite=Lax` (CSRF protection), and a 30-day max age. The `path =
Some("/")` ensures the cookie is sent with every request, not just requests
to `/auth`.

### AuthDirectives

The `AuthDirectives` object provides two Pekko HTTP directives that other
routes use:

```scala
object AuthDirectives extends LazyLogging:

  def authenticated(authService: AuthenticationService)(using
      ExecutionContext
  ): Directive1[AuthContext] =
    optionalCookie("session").flatMap {
      case Some(cookie) =>
        onSuccess(authService.validateSession(cookie.value))
          .flatMap {
            case Right(context) =>
              MDC.put("userId", context.userId.value.toString)
              provide(context)
            case Left(AuthError.AccountInactive) =>
              complete(StatusCodes.Forbidden)
            case Left(_) =>
              complete(StatusCodes.Unauthorized)
          }
      case None =>
        complete(StatusCodes.Unauthorized)
    }

  def requirePermission(
      permission: Permission,
      authService: AuthenticationService
  )(using ExecutionContext): Directive1[AuthContext] =
    authenticated(authService).flatMap { context =>
      if context.hasPermission(permission) then provide(context)
      else
        logger.warn(
          "Permission denied {} {}",
          kv("userId", context.userId.value),
          kv("requiredPermission", permission.key)
        )
        complete(StatusCodes.Forbidden)
    }
```

<small>*File: app/src/main/scala/neon/app/auth/AuthDirectives.scala*</small>

The flow works as follows:

1. **`authenticated`** extracts the session cookie, calls
   `authService.validateSession` (which looks up the session token, checks
   expiry, and loads the user's role and permissions), and provides the
   `AuthContext` to inner routes. If no cookie is present or the session is
   invalid, it returns 401. If the user account is inactive, it returns 403.

2. **`requirePermission`** builds on `authenticated` by checking a specific
   `Permission` value against the user's permission set. If the user lacks
   the required permission, it returns 403 and logs a structured warning.

3. **MDC integration**: `authenticated` puts the `userId` into the SLF4J
   MDC (Mapped Diagnostic Context), so all subsequent log messages in that
   request's Future chain include the user identity. The
   `RequestLoggingDirective` cleans up the MDC after the request completes.

### Request Logging

The `RequestLoggingDirective` wraps every request with structured access
logging:

```scala
def withRequestLogging: Directive0 =
  extractRequest.flatMap { request =>
    val traceId = UuidCreator.getTimeOrderedEpoch().toString
    val startNanos = System.nanoTime()

    MDC.put("traceId", traceId)
    MDC.put("httpMethod", request.method.value)
    MDC.put("httpPath", request.uri.path.toString)

    handleExceptions(loggingExceptionHandler(...)) &
      mapResponse { response =>
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        // Log structured fields: traceId, method, path,
        // status, durationMs, queryString, userId
        response
      }
  }
```

<small>*File: app/src/main/scala/neon/app/http/RequestLoggingDirective.scala*</small>

Every request gets a UUID v7 trace ID (time-ordered for easy sorting). The
directive logs at INFO for 2xx/3xx responses, WARN for 4xx, and ERROR for
5xx. An exception handler catches unhandled errors and logs them with a full
stack trace before returning 500.

> **Note:** The trace ID, HTTP method, and path are also set as MDC fields.
> Combined with `MdcExecutionContext` (which propagates MDC across Future
> boundaries), this means every log line emitted during request processing
> carries the trace ID. You can correlate a single request across service
> calls, repository queries, and projection updates.


## The Full Route Catalogue

For reference, here is every route object in the system and the endpoints it
exposes:

| Route Object | Path Prefix | Operations |
|---|---|---|
| `AuthRoutes` | `/auth` | Login, logout, current user |
| `WaveRoutes` | `/waves` | Plan-and-release, cancel |
| `TaskRoutes` | `/tasks` | Complete, allocate, assign, cancel |
| `TransportOrderRoutes` | `/transport-orders` | Confirm, cancel |
| `ConsolidationGroupRoutes` | `/consolidation-groups` | Complete, cancel |
| `WorkstationRoutes` | `/workstations` | Assign, release, enable, disable |
| `HandlingUnitRoutes` | `/handling-units` | Lifecycle operations |
| `SlotRoutes` | `/slots` | Reserve, complete, release |
| `InventoryRoutes` | `/inventory` | Create, reserve, release, consume |
| `StockPositionRoutes` | `/stock-positions` | Create, allocate, deallocate |
| `InboundRoutes` | `/inbound` | Create, receive, close |
| `CycleCountRoutes` | `/cycle-counts` | Create, start, complete, cancel |

All routes follow the same structure we explored in `WaveRoutes`:
authenticate, parse request, call async service, match on `Either`, and
return the appropriate status code. The consistency makes the codebase easy
to navigate. If you understand one route file, you understand them all.


## What Comes Next

We have now seen every layer of the Neon WES backend: domain aggregates with
typestate encoding, events, policies, services, repositories, event-sourced
actors, CQRS projections, and the HTTP API. In the next chapter, we will
turn our attention to testing. We will explore how each layer is tested
independently, from pure domain tests with zero dependencies all the way up
to integration tests with real databases and HTTP assertions.
