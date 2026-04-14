# The Bootstrap: Wiring It All Together

We have built a lot of pieces so far. Domain aggregates with typestate
encoding, event-sourced actors, cluster sharding repositories, CQRS
projections, HTTP routes, and authentication. Each piece works in isolation,
tested with its own focused test suite. But how does the application actually
start? How do all these pieces come together into a running system?

In this chapter, we will walk through the bootstrap sequence: the two files
that wire Neon WES from a collection of modules into a live application.


## The Startup Sequence

When the JVM starts, Pekko creates the actor system and instantiates the
`Guardian` actor. This is the root of the entire application. Let's look at
what it does.

```scala
object Guardian:

  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      given ActorSystem[Nothing] = context.system
      given Timeout = 15.seconds
      given ExecutionContext =
        MdcExecutionContext(context.system.executionContext)

      FlywayMigration.migrate(context.system.settings.config)

      val connectionFactory =
        ConnectionFactoryProvider(context.system)
          .connectionFactoryFor(
            "pekko.persistence.r2dbc.connection-factory"
          )

      val registry =
        ServiceRegistry(context.system, connectionFactory)

      projection.ProjectionBootstrap.start(context.system)
      http.HttpServer.start(registry, context.system)

      context.log.info("Neon WES Guardian started")

      Behaviors.empty
    }
```

<small>*File: app/src/main/scala/neon/app/Guardian.scala*</small>

That is the entire file. Forty-three lines including package declaration and
imports. Let's break down what happens in order.


## Step 1: Establish the Execution Environment

The first three lines inside `Behaviors.setup` establish the givens (Scala 3
implicit values) that every downstream component needs:

```scala
given ActorSystem[Nothing] = context.system
given Timeout = 15.seconds
given ExecutionContext =
  MdcExecutionContext(context.system.executionContext)
```

The `ActorSystem` given is required by cluster sharding, HTTP binding, and
projection setup. The `Timeout` given sets the default ask timeout for
actor interactions (how long a repository call will wait for an actor
response before failing). The `ExecutionContext` given wraps the default
dispatcher in `MdcExecutionContext`, which propagates SLF4J MDC fields
across `Future` chains. We will cover MDC propagation in detail in
Chapter 19.

> **Note:** The 15-second timeout is generous for a warehouse system.
> Most actor operations complete in single-digit milliseconds. The long
> timeout exists to accommodate cluster rebalancing scenarios where an
> actor might need to recover from its journal before processing the
> first command.


## Step 2: Run Database Migrations

```scala
FlywayMigration.migrate(context.system.settings.config)
```

Before anything else touches the database, Flyway runs schema migrations.
The migration reads connection parameters from the same configuration path
that R2DBC uses (`pekko.persistence.r2dbc.connection-factory`), constructs
a JDBC URL from them, and runs all migration scripts found in
`classpath:db`:

```scala
object FlywayMigration:

  def migrate(config: Config): Unit =
    val c = config.getConfig(
      "pekko.persistence.r2dbc.connection-factory"
    )
    val url =
      s"jdbc:postgresql://${c.getString("host")}:${c.getInt("port")}/${c.getString("database")}"

    Flyway
      .configure()
      .dataSource(url, c.getString("user"), c.getString("password"))
      .locations("classpath:db")
      .load()
      .migrate()
```

<small>*File: app/src/main/scala/neon/app/FlywayMigration.scala*</small>

> **Note:** Flyway uses a blocking JDBC connection, while the rest of
> the application uses non-blocking R2DBC. This is intentional. Migrations
> run once at startup before any async work begins. There is no reason to
> introduce async complexity for a synchronous, sequential operation.


## Step 3: Obtain the R2DBC Connection Factory

```scala
val connectionFactory =
  ConnectionFactoryProvider(context.system)
    .connectionFactoryFor(
      "pekko.persistence.r2dbc.connection-factory"
    )
```

The `ConnectionFactory` is an R2DBC concept: a pool of non-blocking
database connections. Pekko's `ConnectionFactoryProvider` creates and
manages this pool based on the configuration we saw in `application.conf`.
This single connection factory instance gets shared across all repositories
and serves as the foundation for every database interaction in the system.


## Step 4: Build the Service Registry

```scala
val registry =
  ServiceRegistry(context.system, connectionFactory)
```

This single line triggers the construction of every repository and service
in the application. Let's look at what `ServiceRegistry` actually does.


## The Composition Root

`ServiceRegistry` is the composition root for Neon WES. It is a plain class
(not an actor, not a framework component) that constructs and wires all
dependencies by hand:

```scala
class ServiceRegistry(
    system: ActorSystem[?],
    connectionFactory: ConnectionFactory
)(using Timeout, ExecutionContext):

  private given ActorSystem[?] = system

  // --- Actor-backed repositories ---

  val waveRepository = PekkoWaveRepository(system)
  val taskRepository = PekkoTaskRepository(system, connectionFactory)
  val consolidationGroupRepository =
    PekkoConsolidationGroupRepository(system, connectionFactory)
  // ... 11 more actor-backed repositories
```

<small>*File: app/src/main/scala/neon/app/ServiceRegistry.scala*</small>

Let's examine the three layers it constructs.

### Layer 1: Actor-Backed Repositories

The first section creates 14 Pekko repositories:

```scala
val waveRepository = PekkoWaveRepository(system)
val taskRepository = PekkoTaskRepository(system, connectionFactory)
val consolidationGroupRepository =
  PekkoConsolidationGroupRepository(system, connectionFactory)
val transportOrderRepository =
  PekkoTransportOrderRepository(system, connectionFactory)
val handlingUnitRepository = PekkoHandlingUnitRepository(system)
val workstationRepository =
  PekkoWorkstationRepository(system, connectionFactory)
val slotRepository = PekkoSlotRepository(system, connectionFactory)
val inventoryRepository =
  PekkoInventoryRepository(system, connectionFactory)
val stockPositionRepository =
  PekkoStockPositionRepository(system, connectionFactory)
val handlingUnitStockRepository =
  PekkoHandlingUnitStockRepository(system, connectionFactory)
val inboundDeliveryRepository =
  PekkoInboundDeliveryRepository(system)
val goodsReceiptRepository =
  PekkoGoodsReceiptRepository(system)
val cycleCountRepository =
  PekkoCycleCountRepository(system)
val countTaskRepository =
  PekkoCountTaskRepository(system, connectionFactory)
```

Each `PekkoXxxRepository` constructor does something critical: it calls
`ClusterSharding(system).init(Entity(...))`, which registers the actor type
with the cluster sharding system. This is the moment when the cluster becomes
aware that actors of this type exist and can be created on demand.

Some repositories take only the `system` parameter. These are aggregates
whose repository operations are purely single-entity (send a command to one
actor, get a response). Others also take the `connectionFactory` because
they need to perform cross-entity queries against the CQRS projection
tables, as we discussed in Chapter 11.

> **Note:** The order of construction matters subtly here. All actor types
> must be registered via `sharding.init` before any actor tries to send
> messages to another type. Since registration happens in the constructor,
> building all repositories first (before any services) ensures every entity
> type is registered before any cross-aggregate communication begins.

### Layer 2: Reference Data Repositories

```scala
val locationRepository = R2dbcLocationRepository(connectionFactory)
val carrierRepository = R2dbcCarrierRepository(connectionFactory)
val orderRepository = R2dbcOrderRepository(connectionFactory)
val skuRepository = R2dbcSkuRepository(connectionFactory)
val userRepository = R2dbcUserRepository(connectionFactory)
val waveDispatchAssignmentRepository =
  R2dbcWaveDispatchAssignmentRepository(connectionFactory)
```

Reference data modules (locations, carriers, orders, SKUs, users, wave
dispatch assignments) do not use event sourcing or actors. They are simple
R2DBC repositories that query and write to PostgreSQL tables directly. They
take only the `connectionFactory` since there are no actors to register.

### Layer 3: Async Services

With all repositories in place, we can construct the services that
orchestrate business operations:

```scala
val waveReleaseService = AsyncWaveReleaseService(
  waveRepository,
  taskRepository,
  consolidationGroupRepository
)

val taskCompletionService = AsyncTaskCompletionService(
  taskRepository,
  waveRepository,
  consolidationGroupRepository,
  transportOrderRepository,
  VerificationProfile.disabled
)

val waveCancellationService = AsyncWaveCancellationService(
  waveRepository,
  taskRepository,
  transportOrderRepository,
  consolidationGroupRepository
)
```

Each service receives exactly the repositories it needs. The dependency
graph is explicit and visible: you can see at a glance that
`taskCompletionService` talks to tasks, waves, consolidation groups,
and transport orders. No service has access to repositories it does not
use.

The registry constructs 17 async services in total, covering wave
planning and release, task lifecycle management, wave and task
cancellation, transport order confirmation and cancellation,
consolidation group completion and cancellation, workstation assignment
and lifecycle, handling unit management, slot management, inventory,
stock positions, inbound delivery, and cycle counting.

### Layer 4: Authentication

```scala
val passwordHasher = PasswordHasher()
val sessionRepository =
  R2dbcSessionRepository(connectionFactory)
val permissionRepository =
  R2dbcPermissionRepository(connectionFactory)
val authenticationService = AuthenticationService(
  userRepository,
  sessionRepository,
  permissionRepository,
  passwordHasher
)
```

The authentication layer is constructed last because it depends on
`userRepository` from the reference data layer. The
`AuthenticationService` handles login, session management, and
permission checks, and is passed to every route constructor so routes
can enforce authorization.


## Step 5: Start Projections

```scala
projection.ProjectionBootstrap.start(context.system)
```

With all actor types registered (via the repository constructors in
`ServiceRegistry`), we can safely start the CQRS projections.
`ProjectionBootstrap` initializes 13 projections, one for each event-sourced
aggregate type:

```scala
object ProjectionBootstrap:

  def start(system: ActorSystem[?]): Unit =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext

    initProjection[TaskEvent](
      "task-projection", "Task",
      () => TaskProjectionHandler()
    )

    initProjection[ConsolidationGroupEvent](
      "consolidation-group-projection", "ConsolidationGroup",
      () => ConsolidationGroupProjectionHandler()
    )

    // ... 11 more projections
```

<small>*File: app/src/main/scala/neon/app/projection/ProjectionBootstrap.scala*</small>

Each projection is initialized through `ShardedDaemonProcess`, which
creates a projection actor managed by the cluster. The `initProjection`
helper wires the event source provider (reading from the R2DBC journal)
to the projection handler (writing to read-side tables).


## Step 6: Start the HTTP Server

```scala
http.HttpServer.start(registry, context.system)
```

The HTTP server is the final piece. It takes the `ServiceRegistry` and
constructs all route handlers, each receiving exactly the services it needs:

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
        // ... 10 more route handlers
      )
```

<small>*File: app/src/main/scala/neon/app/http/HttpServer.scala*</small>

Every route is wrapped in `RequestLoggingDirective.withRequestLogging`, which
emits the canonical log line we will discuss in Chapter 19.


## Step 7: Done

```scala
context.log.info("Neon WES Guardian started")
Behaviors.empty
```

After all setup is complete, the Guardian logs a startup message and returns
`Behaviors.empty`. This means the Guardian actor itself processes no further
messages. Its entire purpose was the side effects in `Behaviors.setup`:
running migrations, constructing the dependency graph, and starting
subsystems. Once that work is done, the Guardian simply exists as the root of
the actor hierarchy.


## Why Manual Wiring?

Neon WES does not use a dependency injection framework. No Guice, no Spring,
no MacWire, no ZIO ZLayer. The `ServiceRegistry` is plain Scala code that
calls constructors and passes values. This is a deliberate choice with
several benefits.

**Compile-time safety.** If a service needs a repository that does not exist,
the code will not compile. DI frameworks defer wiring to runtime, where
missing bindings become runtime exceptions.

**Explicit dependency graph.** You can read `ServiceRegistry` top to bottom
and understand exactly what depends on what. There is no implicit resolution,
no annotation scanning, no configuration files to correlate.

**Easy navigation.** Click on any constructor call and your IDE takes you to
the implementation. No framework indirection to navigate through.

**Simple testing.** Tests construct their own dependencies directly, as we
saw in Chapter 14. No test framework integration, no mock framework, no
container setup.

The cost is that adding a new service means adding a line to
`ServiceRegistry` and passing it to the relevant route. In a system with
14 repositories, 17 services, and 12 route handlers, this is a small price
for the clarity it provides.

> **Note:** The entire bootstrap (Guardian + ServiceRegistry) is under 220
> lines of code. This is the sum total of "framework glue" in the
> application. Everything else is domain logic, infrastructure
> implementations, or HTTP routes.


## The Dependency Graph in Practice

Let's trace the full dependency chain for a single operation: completing a
task via `POST /tasks/:id/complete`.

1. The HTTP request arrives at `TaskRoutes`, which was constructed with
   `taskCompletionService` and `authenticationService`.
2. `TaskRoutes` calls `authenticationService.authenticate` to verify the
   session cookie and check the `TaskComplete` permission.
3. `TaskRoutes` calls `taskCompletionService.complete(taskId, ...)`.
4. `AsyncTaskCompletionService` was constructed with `taskRepository`,
   `waveRepository`, `consolidationGroupRepository`, and
   `transportOrderRepository`.
5. Each repository call (like `taskRepository.findById`) sends an `ask`
   message to the corresponding actor via cluster sharding.
6. The actor processes the command, persists events to the R2DBC journal,
   and replies.
7. The service orchestrates the cascade: shortpick check, routing,
   wave completion, picking completion.
8. The result flows back through the `Future` chain to the route, which
   maps it to an HTTP response.

Every link in this chain was established by `ServiceRegistry`. The route
knows its services. The services know their repositories. The repositories
know the actor system. And the actor system knows the database connection.
No magic, no reflection, no runtime surprises.


## Summary

The Neon WES bootstrap is remarkably simple:

- `Guardian` runs a linear sequence: migrate, connect, wire, project, serve.
- `ServiceRegistry` is the composition root: 14 actor repositories,
  6 reference data repositories, 17 async services, and authentication.
- Each `PekkoXxxRepository` constructor registers its actor type with
  cluster sharding.
- `ProjectionBootstrap` starts 13 CQRS projections after all actors are
  registered.
- `HttpServer` constructs routes from the registry and binds the server.
- Manual wiring provides compile-time safety, explicit dependencies, and
  easy navigation.

The bootstrap is the thinnest possible layer between the domain and the
running system. It constructs, it wires, and then it gets out of the way.
