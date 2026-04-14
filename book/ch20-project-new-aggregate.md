# Project: Adding a New Aggregate Module

This is our third and final project chapter. Instead of building a specific
warehouse workflow, we will construct a complete checklist for adding an
entirely new event-sourced aggregate module to Neon WES. This chapter
synthesizes every concept from the preceding eighteen chapters into a single,
ordered procedure.

Here is what we are applying:

1. **Opaque type IDs** (Chapter 3): we will create a new ID type in `common`
   for compile-time safety.
2. **Typestate encoding** (Chapter 4): the new aggregate's lifecycle will be
   modeled as a sealed trait hierarchy with state-specific case classes.
3. **Events** (Chapter 5): each state transition will produce an immutable
   event extending `CborSerializable`.
4. **Policies** (Chapter 6): pure decision functions in `core` will encode
   business rules.
5. **Services** (Chapter 7): orchestrators in `core` will coordinate policies
   and repositories.
6. **Repositories** (Chapter 8): sync and async port traits will decouple
   domain logic from infrastructure.
7. **Actors** (Chapter 10): a Pekko `EventSourcedBehavior` will persist events
   and recover state.
8. **Cluster Sharding** (Chapter 11): a `PekkoXxxRepository` will route
   commands to the correct actor via sharding.
9. **Projections** (Chapter 12): a projection handler will materialize events
   into a read-side table.
10. **HTTP routes** (Chapter 13): Pekko HTTP directives will expose the new
    aggregate to the frontend.
11. **Testing** (Chapter 14): suites at every layer will verify correctness
    from pure domain logic up through HTTP responses.
12. **Bootstrap wiring** (Chapter 16): `ServiceRegistry`,
    `ProjectionBootstrap`, and `HttpServer` will learn about the new module.
13. **Serialization** (Chapter 17): Jackson CBOR bindings will handle
    persistence and cluster transport.
14. **Error handling** (Chapter 18): a sealed trait ADT will represent domain
    errors with `Either` return types.

If you follow these steps in order, the compiler will guide you to a working
implementation. Every step produces something testable before you move to the
next.


## The 10-Step Checklist

Before diving into details, here is the full procedure at a glance. Each step
builds on the previous one.

| Step | What                      | Where                          | Produces                          |
|------|---------------------------|--------------------------------|-----------------------------------|
| 1    | sbt subproject            | `build.sbt`                    | Compilable module directory       |
| 2    | Domain aggregate          | `xxx/src/main/scala/`          | Typestate sealed trait            |
| 3    | Events                    | `xxx/src/main/scala/`          | Event sealed trait                |
| 4    | Repository traits         | `xxx/src/main/scala/`          | Sync and async port interfaces    |
| 5    | Actor                     | `xxx/src/main/scala/`          | `EventSourcedBehavior`            |
| 6    | Pekko repository          | `xxx/src/main/scala/`          | Cluster sharding adapter          |
| 7    | Policies and services     | `core/src/main/scala/`         | Business rules, orchestration     |
| 8    | Projection handler        | `app/src/main/scala/projection` | Read-side table population       |
| 9    | HTTP routes               | `app/src/main/scala/http`      | REST endpoints                    |
| 10   | Wiring                    | `app/src/main/scala/`          | Everything connected at startup   |

Let's walk through each step in detail.


## Step 1: The sbt Subproject

Every aggregate in Neon WES lives in its own sbt subproject. This is the first
thing to create.

### Directory structure

The convention is kebab-case for the directory name and concatenated lowercase
for the Scala package:

```
pick-station/
  src/main/scala/neon/pickstation/
  src/test/scala/neon/pickstation/
```

The directory is `pick-station`; the package is `neon.pickstation`. This
convention is consistent across the codebase. `consolidation-group` maps to
`neon.consolidationgroup`, `transport-order` maps to `neon.transportorder`,
and so on.

### build.sbt entry

Add a new `lazy val` in `build.sbt` in the event-sourced aggregate section:

```scala
lazy val pickStation = project
  .in(file("pick-station"))
  .dependsOn(common)
  .settings(
    name := "neon-pick-station",
    libraryDependencies ++= pekkoActorDependencies
  )
```

Three things to note:

1. **Depends on `common`**. Every module does. If your aggregate references
   types from reference data modules (like `LocationId` from `location`), add
   those dependencies too.
2. **Uses `pekkoActorDependencies`**. This shared sequence in `build.sbt`
   includes `pekko-actor-typed`, `pekko-cluster-sharding-typed`,
   `pekko-persistence-typed`, `pekko-serialization-jackson`, and the
   corresponding test dependencies. All eight event-sourced modules use it.
3. **Named with the `neon-` prefix**. All sbt project names follow this
   pattern: `neon-wave`, `neon-task`, `neon-consolidation-group`.

### Wire into the dependency graph

Two more edits in `build.sbt`:

First, add the new module to the `core` project's `dependsOn`:

```scala
lazy val core = project
  .in(file("core"))
  .dependsOn(
    common,
    wave,
    task,
    // ... existing modules ...
    pickStation   // <-- add here
  )
```

Second, add it to the root project's `aggregate`:

```scala
lazy val root = project
  .in(file("."))
  .aggregate(
    common,
    // ... existing modules ...
    pickStation,  // <-- add here
    core,
    app
  )
```

> **Note:** Run `sbt compile` after this step. The project should compile with
> an empty source directory. If it does, the sbt wiring is correct.


## Step 2: The Domain Aggregate

This is the heart of the module. We model the aggregate's lifecycle as a
sealed trait hierarchy using typestate encoding, exactly as we did for `Wave`
in Chapter 4.

### The sealed trait

Create `PickStation.scala` in `pick-station/src/main/scala/neon/pickstation/`:

```scala
package neon.pickstation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.PickStationId

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait PickStation:
  def id: PickStationId
```

Three requirements that every aggregate sealed trait must satisfy:

1. **`@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)`** on the sealed trait. This
   enables Jackson CBOR to deserialize polymorphic snapshots. Without it,
   snapshot recovery will fail with a serialization error.
2. **Extends nothing domain-specific.** The `@JsonTypeInfo` annotation handles
   serialization; the sealed trait itself stays clean.
3. **Declares the ID accessor.** Every state case class inherits this.

### State case classes in the companion object

```scala
object PickStation:

  case class Idle(
      id: PickStationId,
      locationId: LocationId
  ) extends PickStation:

    def activate(at: Instant): (Active, PickStationEvent.Activated) =
      val active = Active(id, locationId)
      val event = PickStationEvent.Activated(id, locationId, at)
      (active, event)

  case class Active(
      id: PickStationId,
      locationId: LocationId
  ) extends PickStation:

    def deactivate(at: Instant): (Idle, PickStationEvent.Deactivated) =
      val idle = Idle(id, locationId)
      val event = PickStationEvent.Deactivated(id, at)
      (idle, event)
```

The rules from Chapter 4 apply:

- **Transition methods exist only on valid source states.** `activate` lives
  on `Idle`, not on `Active`. The compiler enforces this.
- **Every transition returns a `(NewState, Event)` tuple.** The caller
  receives both the updated aggregate and the fact that describes what
  happened.
- **Terminal states have no transition methods.** If your aggregate has a
  `Decommissioned` terminal state, that case class is a plain data holder
  with no methods.

### Factory method with preconditions

If the aggregate needs validation at creation time, use `require()`:

```scala
object PickStation:
  def create(
      id: PickStationId,
      locationId: LocationId
  ): (Idle, PickStationEvent.Created) =
    require(id != null, "PickStationId must not be null")
    val idle = Idle(id, locationId)
    val event = PickStationEvent.Created(id, locationId, Instant.now())
    (idle, event)
```

> **Note:** The opaque type ID for `PickStationId` must be added to the
> `common` module first. Create a new file
> `common/src/main/scala/neon/common/PickStationId.scala` following the
> pattern established by `WaveId`, `TaskId`, and the other thirty-odd ID
> types in that directory.


## Step 3: Events

Events are the source of truth in an event-sourced system. They are stored in
the journal and replayed on actor recovery.

Create `PickStationEvent.scala`:

```scala
package neon.pickstation

import neon.common.PickStationId
import neon.common.serialization.CborSerializable

import java.time.Instant

sealed trait PickStationEvent extends CborSerializable:
  def pickStationId: PickStationId
  def occurredAt: Instant

object PickStationEvent:

  case class Created(
      pickStationId: PickStationId,
      locationId: LocationId,
      occurredAt: Instant
  ) extends PickStationEvent

  case class Activated(
      pickStationId: PickStationId,
      locationId: LocationId,
      occurredAt: Instant
  ) extends PickStationEvent

  case class Deactivated(
      pickStationId: PickStationId,
      occurredAt: Instant
  ) extends PickStationEvent
```

The conventions from Chapter 5:

- **Sealed trait extends `CborSerializable`.** This registers the events for
  Jackson CBOR serialization. Without this marker trait, Pekko will reject
  them at persistence time.
- **One event per transition.** The aggregate's `activate` method produces
  `Activated`. The `deactivate` method produces `Deactivated`. There is a
  one-to-one mapping.
- **Past tense naming.** Events describe something that already happened:
  `Created`, `Activated`, `Deactivated`. Never `Create` or `Activate`.
- **Shared fields.** Every event carries the aggregate ID and an `occurredAt`
  timestamp. These are declared on the sealed trait so projections can access
  them generically.


## Step 4: Repository Traits

Repository traits define the ports through which services interact with
persistence. We create two: one synchronous (for in-memory test
implementations) and one asynchronous (for the Pekko actor-backed
implementation).

### Sync port

Create `PickStationRepository.scala`:

```scala
package neon.pickstation

import neon.common.PickStationId

trait PickStationRepository:
  def findById(id: PickStationId): Option[PickStation]
  def save(pickStation: PickStation, event: PickStationEvent): Unit
```

### Async port

Create `AsyncPickStationRepository.scala`:

```scala
package neon.pickstation

import neon.common.PickStationId
import scala.concurrent.Future

trait AsyncPickStationRepository:
  def findById(id: PickStationId): Future[Option[PickStation]]
  def save(pickStation: PickStation, event: PickStationEvent): Future[Unit]
```

Both traits share the same method signatures. The only difference is the
return type: `Option[PickStation]` versus `Future[Option[PickStation]]`.

Add query methods as the domain requires them. If a service needs to find all
active pick stations, add `def findAllActive: Future[List[PickStation]]` to
the async trait. If that query spans multiple entities, it will use projection
tables rather than individual actor asks.


## Step 5: The Actor

The actor is the event-sourced persistence mechanism. It receives commands,
delegates to domain transition methods, persists events, and recovers state
on restart.

Create `PickStationActor.scala`:

```scala
package neon.pickstation

import neon.common.serialization.CborSerializable
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.*

object PickStationActor:

  // --- Entity key for cluster sharding ---
  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("PickStation")

  // --- Commands ---
  sealed trait Command extends CborSerializable

  case class Create(
      idle: PickStation.Idle,
      event: PickStationEvent.Created,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  case class Activate(
      at: Instant,
      replyTo: ActorRef[StatusReply[ActivateResponse]]
  ) extends Command

  case class GetState(replyTo: ActorRef[Option[PickStation]]) extends Command

  // --- Responses ---
  case class ActivateResponse(
      active: PickStation.Active,
      event: PickStationEvent.Activated
  ) extends CborSerializable

  // --- Actor state ---
  sealed trait State extends CborSerializable
  case object EmptyState extends State
  case class ActiveState(pickStation: PickStation) extends State
```

Five structural elements are required for every actor:

1. **`EntityTypeKey`**: the cluster sharding identifier. The string must be
   unique across all entity types in the system.
2. **Commands**: a sealed trait extending `CborSerializable`. Each command
   carries a `replyTo` for the ask pattern.
3. **Responses**: case classes wrapping the domain result and event, also
   extending `CborSerializable`.
4. **State**: `EmptyState` (before first event) and `ActiveState(aggregate)`.
   This two-case pattern is consistent across all actors.
5. **Behavior factory**: the `apply` method.

### The behavior

```scala
  def apply(entityId: String): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> "PickStation", "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        EventSourcedBehavior
          .withEnforcedReplies[Command, PickStationEvent, State](
            persistenceId = PersistenceId(EntityKey.name, entityId),
            emptyState = EmptyState,
            commandHandler = commandHandler(context),
            eventHandler = eventHandler
          )
          .withRetention(
            RetentionCriteria.snapshotEvery(100, 2)
          )
```

Three things to note:

- **`withEnforcedReplies`** ensures every command handler returns a
  `ReplyEffect`. If you forget to reply, the compiler catches it.
- **`PersistenceId(EntityKey.name, entityId)`** combines the entity type name
  with the entity ID. This is the journal key.
- **`RetentionCriteria.snapshotEvery(100, 2)`** takes a snapshot every 100
  events and keeps the 2 most recent snapshots. This is the project standard.

### Command handler

The command handler delegates to domain methods and pattern-matches on
`(State, Command)` pairs:

```scala
  private def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => ReplyEffect[PickStationEvent, State] =
    (state, command) =>
      (state, command) match
        case (EmptyState, Create(idle, event, replyTo)) =>
          Effect
            .persist(event)
            .thenReply(replyTo)(_ => StatusReply.ack())

        case (ActiveState(idle: PickStation.Idle), Activate(at, replyTo)) =>
          val (active, event) = idle.activate(at)
          Effect
            .persist(event)
            .thenReply(replyTo)(_ =>
              StatusReply.success(ActivateResponse(active, event))
            )

        case (_, GetState(replyTo)) =>
          val result = state match
            case EmptyState              => None
            case ActiveState(ps) => Some(ps)
          Effect.reply(replyTo)(result)

        case (_, cmd) =>
          // Invalid command for current state
          val msg = s"Invalid command ${cmd.getClass.getSimpleName} " +
            s"in state ${state.getClass.getSimpleName}"
          context.log.warn(msg)
          // Reply with error on the appropriate replyTo
          cmd match
            case c: Create   => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: Activate => Effect.reply(c.replyTo)(StatusReply.error(msg))
            case c: GetState => Effect.reply(c.replyTo)(None)
```

The catch-all case at the bottom handles invalid state/command combinations by
logging a warning and replying with an error. This is the standard pattern
across all actors.

### Event handler

The event handler reconstructs state during recovery. It is a pure function
from `(State, Event) => State`:

```scala
  private val eventHandler: (State, PickStationEvent) => State =
    (state, event) =>
      event match
        case e: PickStationEvent.Created =>
          ActiveState(PickStation.Idle(e.pickStationId, e.locationId))
        case e: PickStationEvent.Activated =>
          ActiveState(PickStation.Active(e.pickStationId, e.locationId))
        case e: PickStationEvent.Deactivated =>
          state match
            case ActiveState(ps) =>
              ActiveState(PickStation.Idle(ps.id, ps.asInstanceOf[PickStation.Active].locationId))
            case _ => state
```

> **Note:** The event handler must be able to reconstruct any valid state from
> events alone. If a field exists on the aggregate, some event must carry it.
> This is the "event must contain enough data" rule from Chapter 5.


## Step 6: The Pekko Repository

The Pekko repository implements the async port trait using cluster sharding.
It is the bridge between the service layer and the actor layer.

Create `PekkoPickStationRepository.scala`:

```scala
package neon.pickstation

import neon.common.PickStationId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout
import scala.concurrent.{ExecutionContext, Future}

class PekkoPickStationRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncPickStationRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(PickStationActor.EntityKey)(ctx =>
    PickStationActor(ctx.entityId)
  ))

  def findById(id: PickStationId): Future[Option[PickStation]] =
    sharding
      .entityRefFor(PickStationActor.EntityKey, id.value.toString)
      .ask(PickStationActor.GetState(_))

  def save(
      pickStation: PickStation,
      event: PickStationEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      PickStationActor.EntityKey,
      pickStation.id.value.toString
    )
    event match
      case e: PickStationEvent.Created =>
        entityRef
          .askWithStatus(
            PickStationActor.Create(
              PickStation.Idle(pickStation.id, e.locationId),
              e,
              _
            )
          )
          .map(_ => ())
      // ... additional event cases ...
```

The constructor calls `sharding.init`, which registers the entity type with
cluster sharding. This happens once when the repository is instantiated in
`ServiceRegistry`.

For single-entity operations, we use `entityRefFor(...).ask(...)`. For
cross-entity queries (like "find all active pick stations"), we query the
CQRS projection table and then fan out to individual actors if needed. The
`R2dbcProjectionQueries` trait from `common` provides this capability.


## Step 7: Policies and Services (in core)

The `core` module is where business rules and orchestration live. Policies are
pure functions; services coordinate policies with repositories.

### Error ADT

Define the domain errors first:

```scala
package neon.core

sealed trait PickStationError
object PickStationError:
  case class NotFound(id: PickStationId) extends PickStationError
  case class InvalidState(id: PickStationId, state: String)
      extends PickStationError
```

### Policy

Policies are stateless objects that make decisions. They accept domain
objects and return `Option[(NewState, Event)]`:

```scala
package neon.core

object PickStationActivationPolicy:
  def evaluate(
      station: PickStation,
      at: Instant
  ): Option[(PickStation.Active, PickStationEvent.Activated)] =
    station match
      case idle: PickStation.Idle => Some(idle.activate(at))
      case _                     => None
```

### Sync service

```scala
package neon.core

class PickStationService(repository: PickStationRepository):

  def activate(
      id: PickStationId,
      at: Instant
  ): Either[PickStationError, PickStation.Active] =
    repository.findById(id) match
      case None =>
        Left(PickStationError.NotFound(id))
      case Some(station) =>
        PickStationActivationPolicy.evaluate(station, at) match
          case None =>
            Left(PickStationError.InvalidState(id, station.getClass.getSimpleName))
          case Some((active, event)) =>
            repository.save(active, event)
            Right(active)
```

### Async service

The async counterpart uses `Future` and the async repository:

```scala
package neon.core

import scala.concurrent.{ExecutionContext, Future}

class AsyncPickStationService(
    repository: AsyncPickStationRepository
)(using ExecutionContext):

  def activate(
      id: PickStationId,
      at: Instant
  ): Future[Either[PickStationError, PickStation.Active]] =
    repository.findById(id).flatMap:
      case None =>
        Future.successful(Left(PickStationError.NotFound(id)))
      case Some(station) =>
        PickStationActivationPolicy.evaluate(station, at) match
          case None =>
            Future.successful(
              Left(PickStationError.InvalidState(id, station.getClass.getSimpleName))
            )
          case Some((active, event)) =>
            repository.save(active, event).map(_ => Right(active))
```

The sync service is for testing with in-memory repositories. The async service
is what the HTTP routes call in production.


## Step 8: Projection Handler (in app)

The projection handler consumes events from the journal and populates a
read-side table for queries.

### The Flyway migration

Create a SQL migration file in the Flyway migrations directory:

```sql
CREATE TABLE pick_station_by_location (
    pick_station_id UUID PRIMARY KEY,
    location_id     UUID NOT NULL,
    state           VARCHAR(50) NOT NULL
);

CREATE INDEX idx_pick_station_location
    ON pick_station_by_location (location_id);
```

### The handler

Create `PickStationProjectionHandler.scala` in `app/src/main/scala/neon/app/projection/`:

```scala
package neon.app.projection

import neon.pickstation.PickStationEvent
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession

import scala.concurrent.{ExecutionContext, Future}

class PickStationProjectionHandler(using ExecutionContext)
    extends LoggingProjectionHandler[PickStationEvent]:

  override protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[PickStationEvent]
  ): Future[Done] =
    envelope.event match
      case e: PickStationEvent.Created =>
        val stmt = session.createStatement(
          """INSERT INTO pick_station_by_location
            |  (pick_station_id, location_id, state)
            |VALUES ($1, $2, $3)
            |ON CONFLICT (pick_station_id) DO UPDATE SET state = $3
            |""".stripMargin
        )
        stmt.bind(0, e.pickStationId.value)
        stmt.bind(1, e.locationId.value)
        stmt.bind(2, "Idle")
        session.updateOne(stmt).map(_ => Done)

      case e: PickStationEvent.Activated =>
        updateState(session, e.pickStationId.value, "Active")

      case e: PickStationEvent.Deactivated =>
        updateState(session, e.pickStationId.value, "Idle")

  private def updateState(
      session: R2dbcSession,
      id: java.util.UUID,
      state: String
  ): Future[Done] =
    val stmt = session
      .createStatement(
        """UPDATE pick_station_by_location
          |SET state = $1 WHERE pick_station_id = $2""".stripMargin
      )
      .bind(0, state)
      .bind(1, id)
    session.updateOne(stmt).map(_ => Done)
```

The handler extends `LoggingProjectionHandler`, which adds structured DEBUG
logging on event entry and ERROR logging with stack traces on failure.
Subclasses only need to implement `processEvent`.


## Step 9: HTTP Routes (in app)

The routes expose the aggregate to the frontend. They handle JSON
serialization, authentication, and error-to-status-code mapping.

### Request and response DTOs

```scala
package neon.app.http

import io.circe.{Decoder, Encoder}

object PickStationRoutes:

  case class CreateRequest(
      locationId: String
  ) derives Decoder

  case class PickStationResponse(
      status: String,
      pickStationId: String
  ) derives Encoder.AsObject
```

DTOs use `derives Decoder` and `derives Encoder.AsObject` for automatic circe
codec derivation. Keep DTOs in the routes companion object to co-locate them
with their usage.

### Route definition

```scala
  def apply(
      service: AsyncPickStationService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("pick-stations"):
      concat(
        pathEnd:
          post:
            AuthDirectives.authenticated(authService): session =>
              entity(as[CreateRequest]): request =>
                // create logic
                complete(StatusCodes.Created -> response)
        ,
        path(Segment): id =>
          post:
            path("activate"):
              AuthDirectives.authenticated(authService): session =>
                // activation logic
                onSuccess(service.activate(pickStationId, Instant.now())):
                  case Right(active) =>
                    complete(StatusCodes.OK -> response)
                  case Left(PickStationError.NotFound(_)) =>
                    complete(StatusCodes.NotFound)
                  case Left(PickStationError.InvalidState(_, _)) =>
                    complete(StatusCodes.Conflict)
      )
```

The error mapping follows the conventions from Chapter 18:

- `NotFound` maps to `404 Not Found`
- `InvalidState` maps to `409 Conflict`
- Validation errors map to `400 Bad Request`

> **Note:** Remember to import `CirceSupport.given` in the routes file. This
> brings the implicit marshallers that bridge circe codecs with Pekko HTTP's
> marshalling infrastructure.


## Step 10: Wiring

The final step connects everything at application startup. Three files need
edits.

### ServiceRegistry

Add the repository and service in `app/src/main/scala/neon/app/ServiceRegistry.scala`:

```scala
// In the actor-backed repositories section:
val pickStationRepository = PekkoPickStationRepository(system)

// In the services section:
val pickStationService = AsyncPickStationService(pickStationRepository)
```

The `ServiceRegistry` constructor runs at startup inside the `Guardian` actor.
Creating the `PekkoPickStationRepository` triggers `sharding.init`, which
registers the entity type with cluster sharding.

### ProjectionBootstrap

Add the projection in `app/src/main/scala/neon/app/projection/ProjectionBootstrap.scala`:

```scala
initProjection[PickStationEvent](
  "pick-station-projection",
  "PickStation",
  () => PickStationProjectionHandler()
)
```

The three arguments are: a unique projection name, the entity type string
(must match the `EntityTypeKey` name from the actor), and a handler factory.
The entity type string `"PickStation"` must exactly match
`PickStationActor.EntityKey`'s name. If these do not match, the projection
will never receive events.

### HttpServer

Add the route in `app/src/main/scala/neon/app/http/HttpServer.scala`:

```scala
// Inside the concat(...) block in the routes method:
PickStationRoutes(
  registry.pickStationService,
  registry.authenticationService
),
```

> **Note:** After all three wiring edits, run `sbt compile` to verify
> everything resolves. Then run `sbt test` to ensure nothing is broken.


## Testing at Every Layer

Every step above produces something testable. Let's map out the test suites
you need.

### Layer 1: Domain aggregate tests

File: `pick-station/src/test/scala/neon/pickstation/PickStationSuite.scala`

These are pure Scala tests with no framework dependencies beyond ScalaTest.
They verify that typestate transitions produce the correct states and events:

```scala
class PickStationSuite extends AnyFunSpec:

  describe("PickStation"):
    describe("activating"):
      it("transitions from idle to active"):
        val idle = PickStation.Idle(id, locationId)
        val (active, event) = idle.activate(at)
        assert(active.isInstanceOf[PickStation.Active])
        assert(event.pickStationId == id)

    describe("deactivating"):
      it("transitions from active to idle"):
        val idle = PickStation.Idle(id, locationId)
        val (active, _) = idle.activate(at)
        val (deactivated, event) = active.deactivate(at)
        assert(deactivated.isInstanceOf[PickStation.Idle])
```

### Layer 2: Policy tests

File: `core/src/test/scala/neon/core/PickStationActivationPolicySuite.scala`

Policies are pure functions. Tests pass in domain objects and assert on the
returned `Option`:

```scala
class PickStationActivationPolicySuite extends AnyFunSpec:

  describe("PickStationActivationPolicy"):
    it("activates an idle station"):
      val result = PickStationActivationPolicy.evaluate(idle, at)
      assert(result.isDefined)

    it("rejects activation of an already active station"):
      val result = PickStationActivationPolicy.evaluate(active, at)
      assert(result.isEmpty)
```

### Layer 3: Service tests

File: `core/src/test/scala/neon/core/PickStationServiceSuite.scala`

Service tests use in-memory repository implementations with mutable maps:

```scala
class PickStationServiceSuite extends AnyFunSpec:
  def inMemoryRepository = new PickStationRepository:
    private val store = scala.collection.mutable.Map[PickStationId, PickStation]()
    private val events = scala.collection.mutable.ListBuffer[PickStationEvent]()

    def findById(id: PickStationId) = store.get(id)
    def save(ps: PickStation, event: PickStationEvent) =
      store(ps.id) = ps
      events += event

  describe("PickStationService"):
    it("returns NotFound for unknown ID"):
      val service = PickStationService(inMemoryRepository)
      val result = service.activate(PickStationId(), Instant.now())
      assert(result.isLeft)
```

### Layer 4: Actor tests

File: `pick-station/src/test/scala/neon/pickstation/PickStationActorSuite.scala`

Actor tests use `EventSourcedBehaviorTestKit` with serialization verification:

```scala
class PickStationActorSuite
    extends ScalaTestWithActorTestKit(
      ConfigFactory.parseString("""
        pekko.actor {
          provider = local
          serialization-bindings {
            "neon.common.serialization.CborSerializable" = jackson-cbor
          }
        }
      """)
      .withFallback(EventSourcedBehaviorTestKit.config)
      .resolve()
    )
    with AnyFunSpecLike
    with BeforeAndAfterEach:

  private val esTestKit = EventSourcedBehaviorTestKit[
    PickStationActor.Command,
    PickStationEvent,
    PickStationActor.State
  ](system, PickStationActor(id.value.toString))
```

The `EventSourcedBehaviorTestKit` verifies serialization of events and state
without requiring a cluster. This catches serialization issues early, before
they surface in integration tests.

### Layer 5: Route tests

File: `app/src/test/scala/neon/app/http/PickStationRoutesSuite.scala`

Route tests use `ScalatestRouteTest` with stub services:

```scala
class PickStationRoutesSuite
    extends AnyFunSpec
    with ScalatestRouteTest:

  val stubService = new AsyncPickStationService(/* stub repo */)
  val routes = PickStationRoutes(stubService, stubAuthService)

  describe("POST /pick-stations"):
    it("returns 201 Created"):
      Post("/pick-stations", createRequest) ~> routes ~> check:
        assert(status == StatusCodes.Created)
```

> **Note:** Every test layer catches a different category of bug. Domain
> tests catch logic errors. Policy tests catch business rule regressions.
> Service tests catch orchestration mistakes. Actor tests catch serialization
> and persistence issues. Route tests catch HTTP contract violations. Skipping
> any layer leaves a gap.


## What We Learned

Adding a new aggregate module to Neon WES is a ten-step procedure. Each step
is small, testable in isolation, and follows an established pattern. The
directory structure, naming conventions, trait hierarchies, and wiring points
are all consistent across every module in the system.

This consistency is not accidental. The module boundary pattern creates a
natural unit of delivery. A developer can look at `wave/` to understand how
`task/` works, can study `task/` to predict how `consolidation-group/` is
structured, and can use any existing module as a template for a new one.

The ten steps also reveal the dependency ordering of the architecture. The
domain aggregate (Steps 2-3) depends on nothing but `common`. The repository
traits (Step 4) depend only on the domain. The actor (Step 5) depends on the
domain and Pekko. The Pekko repository (Step 6) depends on the actor. The
policies and services (Step 7) depend on the domain and repository traits.
The projection and routes (Steps 8-9) depend on the domain and services. The
wiring (Step 10) ties everything together.

This layering means you can implement and test Steps 2 through 4 before you
write a single line of actor code. You can implement and test the policy and
sync service before the async repository exists. Each layer is independently
verifiable, and the compiler ensures they compose correctly.

Every aggregate follows the same structure. That predictability is what makes
the system navigable at scale.
