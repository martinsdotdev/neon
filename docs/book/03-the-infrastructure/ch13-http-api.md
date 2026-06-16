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
```

<small>_File: app/src/main/scala/neon/app/http/HttpServer.scala_</small>

Several things are worth noting here:

**One route object per domain area.** `TaskRoutes`, `WaveRoutes`,
`WorkstationRoutes`, and so on each live in their own file. This keeps
individual route files small and focused. Two `Mobile*` route objects expose
RF-scanner-shaped endpoints, and `NotificationRoutes` carries the WebSocket
stream that delivers the task-assignment notifications we saw the projection
publish in Chapter 12.

**ServiceRegistry injection.** The `ServiceRegistry` (created by `Guardian`
at startup) holds references to all async services and repositories. Each
route object receives only the services it needs.

**Problem-details handlers wrap everything.** The whole tree is wrapped in
`handleExceptions(ProblemRouteHandlers.exceptionHandler)` and
`handleRejections(ProblemRouteHandlers.rejectionHandler)`. These convert
unhandled exceptions and unmapped rejections (such as the
`AuthorizationFailedRejection` we will meet shortly) into RFC 9457
`application/problem+json` responses, so even failures that never reach a route
body come back in the same error shape. The route tree also needs an
`ActorSystem[?]` in scope, which is why `routes` takes it as a `using`
parameter.

**RequestLoggingDirective wraps everything.** Every request passes through a
logging directive that emits a structured access log line with method, path,
status code, duration, and trace ID. We will look at this in more detail
later.

**Server binding.** The `start` method reads host and port from configuration
and binds the route tree:

```scala
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
```

<small>_File: app/src/main/scala/neon/app/http/HttpServer.scala_</small>

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

<small>_File: app/src/main/scala/neon/app/http/CirceSupport.scala_</small>

Let's unpack this:

**The printer drops null values.** `Printer.noSpaces.copy(dropNullValues =
true)` means optional fields that are `None` simply disappear from the JSON
output rather than appearing as `"field": null`. This produces cleaner
responses.

**Generic givens.** The `given [A: Encoder]` syntax creates a
`ToEntityMarshaller[A]` for _any_ type that has an `Encoder` instance. When
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

<small>_File: app/src/main/scala/neon/app/http/WaveRoutes.scala_</small>

`derives Decoder` generates a `Decoder` instance at compile time using
Scala 3's typeclass derivation. `derives Encoder.AsObject` does the same for
encoding, producing a JSON object (as opposed to a primitive value).

@:callout(info)

DTOs are deliberately separate from domain types. The
`PlanAndReleaseRequest` uses `List[String]` for order IDs, not
`List[OrderId]`. The route is responsible for parsing strings into opaque
type IDs. This keeps the JSON contract stable even if internal ID
representations change.

@:@

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
                  case Left(error) =>
                    completeProblem(error)
```

<small>_File: app/src/main/scala/neon/app/http/WaveRoutes.scala_</small>

Here is the flow, step by step:

1. **Path matching**: `pathPrefix("waves")` and `path("plan-and-release")`
   match the URL `/waves/plan-and-release`.

2. **Authorization**: `AuthDirectives.requirePermission(Permission.WavePlan,
authService)` validates the caller's credential (a bearer token or a session
   cookie) and checks that the user has the `WavePlan` permission. If not, it
   short-circuits with 401 (no/invalid credential) or 403 (authenticated but
   lacking the permission).

3. **Request parsing**: `entity(as[PlanAndReleaseRequest])` uses the circe
   unmarshaller to deserialize the JSON body.

4. **DTO to domain conversion**: The route converts string IDs to opaque type
   IDs (`OrderId`, `LocationId`, `CarrierId`) and the grouping string to an
   `OrderGrouping` enum.

5. **Service call**: The route fetches orders from the repository, then calls
   `wavePlanningService.planAndRelease`. Both operations return `Future`
   values, composed with `flatMap`.

6. **Result mapping**: `onSuccess` unwraps the `Future`. The service returns
   `Either[WavePlanningError, WavePlanningResult]`. On `Right` we build the
   success DTO; on `Left` we hand the error to `completeProblem`, which (via a
   `ProblemMapper` given) turns any `WavePlanningError` into the correct
   problem-details response. The route never names a status code itself.

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
                case Left(error) =>
                  completeProblem(error)
```

<small>_File: app/src/main/scala/neon/app/http/WaveRoutes.scala_</small>

`path(Segment)` captures the wave ID from the URL path. `DELETE
/waves/abc-123-def` extracts `"abc-123-def"` as `waveIdStr`. The rest of the
pattern is identical: authorize, call service, map `Right` to a success DTO and
`Left` through `completeProblem`. Both `WaveRoutes` files import
`ProblemMapper.completeProblem` at the top, alongside `CirceSupport.given`.

## Error Mapping

Neon WES uses sealed trait ADTs for errors (as we saw in Chapter 7). The HTTP
layer maps these domain errors to RFC 9457 _Problem Details_ responses, returned
with the `application/problem+json` content type (ADR 0011). Rather than each
route hand-rolling status codes, the error-to-status knowledge lives at one
seam: the `ProblemMapper` typeclass, with one given instance per error ADT.

A `ProblemDetails` is a small case class carrying a `status`, a `title`, an
optional `detail`, and a `type` URI. For our own errors the `type` is
`urn:neon:error:<slug>`, a stable, machine-readable identifier for the error
kind. The mapping convention behind the status codes is still consistent across
every ADT:

| Domain Error Pattern                    | HTTP Status               | When                                  |
| --------------------------------------- | ------------------------- | ------------------------------------- |
| `XxxNotFound`                           | 404 Not Found             | Entity does not exist                 |
| `XxxAlreadyTerminal`, `XxxInWrongState` | 409 Conflict              | State machine violation               |
| `InvalidXxx`, validation errors         | 422 Unprocessable Entity  | Valid request structure, invalid data |
| `VerificationRequired`                  | 428 Precondition Required | Business precondition not met         |

A route never matches on the error itself. It simply calls
`completeProblem(error)`, and the compiler resolves the right `ProblemMapper`
given for that error type:

```scala
def completeProblem[E](error: E)(using mapper: ProblemMapper[E]): Route =
  val problem = mapper.toProblem(error)
  complete(StatusCode.int2StatusCode(problem.status) -> problem)
```

<small>_File: app/src/main/scala/neon/app/http/ProblemMapper.scala_</small>

The per-ADT knowledge lives in the given instances. Here is the one for
`TaskCompletionError`:

```scala
given ProblemMapper[TaskCompletionError] with
  def toProblem(error: TaskCompletionError): ProblemDetails = error match
    case TaskCompletionError.TaskNotFound(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.NotFound,
        slug = "task-not-found",
        title = "Task not found",
        detail = Some(s"Task ${taskId.value} was not found")
      )
    case TaskCompletionError.TaskNotAssigned(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.Conflict,
        slug = "task-not-assigned",
        title = "Task not assigned",
        detail = Some(
          s"Task ${taskId.value} is not in the Assigned state required for completion"
        )
      )
    case TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity) =>
      ProblemDetails.of(
        status = StatusCodes.UnprocessableEntity,
        slug = "invalid-actual-quantity",
        title = "Invalid actual quantity",
        detail = Some(s"Actual quantity $actualQuantity for task ${taskId.value} is invalid")
      )
    case TaskCompletionError.VerificationRequired(taskId) =>
      ProblemDetails.of(
        status = StatusCodes.PreconditionRequired,
        slug = "verification-required",
        title = "Verification required",
        detail = Some(s"Task ${taskId.value} requires verification before completion")
      )
```

<small>_File: app/src/main/scala/neon/app/http/ProblemMapper.scala_</small>

`ProblemMapper` carries a given for every domain error ADT in the system,
`TaskLifecycleError`, `WavePlanningError`, `InventoryError`, and so on, each
mapping its cases to the appropriate status, slug, title, and human-readable
detail. The `TaskRoutes` and `WaveRoutes` `Left(error) => completeProblem(error)`
branches we saw earlier resolve to these instances at compile time.

This approach has two important benefits. First, the compiler guarantees
exhaustive matching: if someone adds a new case to a sealed error trait, the
`toProblem` match stops compiling until the new case is handled. No error can
silently slip through. Second, the status-and-message decision for each error
exists in exactly one place, so a route file and a mobile route hitting the
same service return byte-identical error responses.

@:callout(info)

Because the mapping lives in `ProblemMapper` rather than in the
routes, every endpoint returns a structured `application/problem+json` body
with a machine-readable `type` and a human-readable `detail`, not a bare status
code. The domain error ADT remains the single source of truth for what can go
wrong, and the unmapped fall-through cases (unhandled exceptions, rejections)
are turned into the same problem-details shape by `ProblemRouteHandlers`.

@:@

## Authentication and Authorization

Every route in Neon WES (except the auth routes themselves) is protected by
authentication with role-based access control (RBAC). A request authenticates
with either a bearer token (used by the mobile client and other non-browser
callers) or a session cookie (used by the web app); the same login issues both.

### Login: Cookie and Token

The `AuthRoutes` object handles login and logout. On successful login, it sets
an HTTP-only session cookie _and_ returns the token in the response body, so a
browser can rely on the cookie while a mobile client reads the token:

```scala
path("login"):
  post:
    entity(as[LoginRequest]): request =>
      extractClientIP: clientIp =>
        optionalHeaderValueByName("User-Agent"): userAgent =>
          onSuccess(
            authService.login(
              login = request.login,
              password = request.password,
              ipAddress =
                Some(clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")),
              userAgent = userAgent
            )
          ):
            case Right((token, context)) =>
              setCookie(sessionCookie(token, secureCookies)):
                complete(
                  AuthResponse.fromContext(context = context, token = Some(token))
                )
            case Left(AuthError.InvalidCredentials) =>
              complete(StatusCodes.Unauthorized)
            case Left(AuthError.AccountInactive) =>
              complete(StatusCodes.Forbidden)
            case Left(_) =>
              complete(StatusCodes.Unauthorized)
```

<small>_File: app/src/main/scala/neon/app/http/AuthRoutes.scala_</small>

The session cookie is configured with security best practices: `httpOnly =
true` (not accessible from JavaScript), `secure = true` in production (HTTPS
only), `SameSite=Lax` (CSRF protection), and a 30-day max age. The `path =
Some("/")` ensures the cookie is sent with every request, not just requests
to `/auth`. The `token` field on the response is present only for clients that
cannot use cookies; browsers ignore it.

### AuthDirectives

The `AuthDirectives` object provides two Pekko HTTP directives that other
routes use:

```scala
object AuthDirectives extends LazyLogging:

  private val bearerPrefix = "Bearer "

  def authenticated(authService: AuthenticationService)(using
      ExecutionContext
  ): Directive1[AuthContext] =
    bearerToken.flatMap {
      case Some(token) => validateToken(authService, token)
      case None        =>
        optionalCookie("session").flatMap {
          case Some(cookie) => validateToken(authService, cookie.value)
          case None         => complete(StatusCodes.Unauthorized)
        }
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
        // Reject instead of completing so Pekko HTTP's concat can fall
        // through to sibling routes. The route-level RejectionHandler in
        // ProblemRouteHandlers converts unmapped AuthorizationFailedRejection
        // into the RFC 9457 problem-details 403 response.
        reject(AuthorizationFailedRejection)
    }

  private def bearerToken: Directive1[Option[String]] =
    optionalHeaderValueByName("Authorization").map {
      case Some(value) if value.startsWith(bearerPrefix) =>
        Some(value.substring(bearerPrefix.length))
      case _ => None
    }

  private def validateToken(
      authService: AuthenticationService,
      token: String
  )(using ExecutionContext): Directive1[AuthContext] =
    onSuccess(authService.validateSession(token)).flatMap {
      case Right(context) =>
        MDC.put("userId", context.userId.value.toString)
        provide(context)
      case Left(AuthError.AccountInactive) =>
        complete(StatusCodes.Forbidden)
      case Left(_) =>
        complete(StatusCodes.Unauthorized)
    }
```

<small>_File: app/src/main/scala/neon/app/auth/AuthDirectives.scala_</small>

The flow works as follows:

1. **`authenticated`** looks for an `Authorization: Bearer <token>` header
   first; if there is none, it falls back to the `session` cookie. Either
   credential is handed to `validateToken`, which calls
   `authService.validateSession` (looking up the token, checking expiry, and
   loading the user's role and permissions) and provides the `AuthContext` to
   inner routes. If neither credential is present, or the token is invalid, it
   returns 401. If the user account is inactive, it returns 403.

2. **`requirePermission`** builds on `authenticated` by checking a specific
   `Permission` value against the user's permission set. If the user lacks the
   required permission, it logs a structured warning and _rejects_ with
   `AuthorizationFailedRejection` rather than completing with 403 directly.
   Rejecting lets Pekko HTTP's `concat` fall through to a sibling route that
   might accept the request; if none does, the `rejectionHandler` in
   `ProblemRouteHandlers` converts the rejection into the RFC 9457 403
   response.

3. **MDC integration**: `validateToken` puts the `userId` into the SLF4J
   MDC (Mapped Diagnostic Context), so all subsequent log messages in that
   request's Future chain include the user identity. The
   `RequestLoggingDirective` restores the previous MDC state after the request
   completes.

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

<small>_File: app/src/main/scala/neon/app/http/RequestLoggingDirective.scala_</small>

Every request gets a UUID v7 trace ID (time-ordered for easy sorting). The
directive logs at INFO for 2xx/3xx responses, WARN for 4xx, and ERROR for
5xx. An exception handler catches unhandled errors and logs them with a full
stack trace before returning 500.

@:callout(info)

The trace ID, HTTP method, and path are also set as MDC fields.
Combined with `MdcExecutionContext` (which propagates MDC across Future
boundaries), this means every log line emitted during request processing
carries the trace ID. You can correlate a single request across service
calls, repository queries, and projection updates.

@:@

## The Full Route Catalogue

For reference, here is every route object in the system and the endpoints it
exposes:

| Route Object               | Path Prefix             | Operations                         |
| -------------------------- | ----------------------- | ---------------------------------- |
| `AuthRoutes`               | `/auth`                 | Login, logout, current user        |
| `WaveRoutes`               | `/waves`                | Plan-and-release, cancel           |
| `TaskRoutes`               | `/tasks`                | Complete, allocate, assign, cancel |
| `MobileTaskRoutes`         | `/tasks`                | RF-scanner task queue, claim       |
| `MobileLookupRoutes`       | `/skus`, `/locations`, `/handling-units` | Scan lookups      |
| `TransportOrderRoutes`     | `/transport-orders`     | Confirm, cancel                    |
| `ConsolidationGroupRoutes` | `/consolidation-groups` | Complete, cancel                   |
| `WorkstationRoutes`        | `/workstations`         | Assign, release, enable, disable   |
| `HandlingUnitRoutes`       | `/handling-units`       | Lifecycle operations               |
| `SlotRoutes`               | `/slots`                | Reserve, complete, release         |
| `InventoryRoutes`          | `/inventory`            | Create, reserve, release, consume  |
| `StockPositionRoutes`      | `/stock-positions`      | Create, allocate, deallocate       |
| `InboundRoutes`            | `/inbound`              | Create, receive, close             |
| `CycleCountRoutes`         | `/cycle-counts`         | Create, start, complete, cancel    |
| `NotificationRoutes`       | `/ws/notifications`     | WebSocket notification stream      |

All routes follow the same structure we explored in `WaveRoutes`:
authenticate, parse request, call async service, map `Right` to a success DTO
and `Left` through `completeProblem`. The consistency makes the codebase easy
to navigate. If you understand one route file, you understand them all.

## What Comes Next

We have now seen every layer of the Neon WES backend: domain aggregates with
typestate encoding, events, policies, services, repositories, event-sourced
actors, CQRS projections, and the HTTP API. In the next chapter, we will
turn our attention to testing. We will explore how each layer is tested
independently, from pure domain tests with zero dependencies all the way up
to integration tests with real databases and HTTP assertions.
