# Serialization

An event-sourced system serializes constantly. Every command sent to an actor,
every event persisted to the journal, every snapshot saved for recovery, every
response returned to the caller. If serialization is wrong, the system
silently corrupts data or fails to recover. In this chapter, we will look at
how Neon WES handles serialization at two boundaries: Jackson CBOR for the
persistence layer and Circe JSON for the HTTP API.


## Two Boundaries, Two Serializers

The persistence layer and the HTTP layer have different requirements.

The **persistence layer** (actor commands, events, snapshots) needs compact
binary encoding, polymorphic type handling, and backward-compatible schema
evolution. Events written today must be readable years from now, even as the
code evolves.

The **HTTP layer** (request and response bodies) needs human-readable JSON,
clean null handling, and integration with the Pekko HTTP marshalling system.
Clients should see predictable, well-structured responses.

Neon WES uses Jackson CBOR for persistence and Circe for HTTP. Let's
examine each.


## Jackson CBOR for Persistence

### The CborSerializable Marker Trait

Every type that passes through Pekko's serialization system must be mapped
to a serializer. Neon WES uses a single marker trait for this:

```scala
/** Marker trait for types serialized via Jackson CBOR in the Pekko
  * journal and cluster. All actor commands, responses, state wrappers,
  * and event envelopes must mix in this trait.
  *
  * The binding to JacksonCborSerializer is declared in serialization.conf.
  */
trait CborSerializable
```

<small>*File: common/src/main/scala/neon/common/serialization/CborSerializable.scala*</small>

That is the entire file. The trait has no methods, no fields, no type
parameters. It exists solely as a type tag that tells Pekko "serialize this
with Jackson CBOR."

### The Configuration Binding

The binding between the marker trait and the serializer lives in
`application.conf`:

```hocon
pekko {
  actor {
    allow-java-serialization = off

    serializers {
      jackson-cbor = "org.apache.pekko.serialization.jackson.JacksonCborSerializer"
    }
    serialization-bindings {
      "neon.common.serialization.CborSerializable" = jackson-cbor
    }
  }

  serialization.jackson {
    jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"
  }
}
```

<small>*File: app/src/main/resources/application.conf*</small>

Three things happen here:

1. **Java serialization is disabled.** The line `allow-java-serialization = off`
   prevents Pekko from falling back to Java's built-in serialization. Java
   serialization is insecure (arbitrary code execution on deserialization),
   fragile (breaks on class changes), and slow. Disabling it means any type
   that lacks a proper serializer binding will fail fast at startup rather
   than silently using a dangerous default.

2. **Jackson CBOR is registered.** The `serializers` block maps the name
   `jackson-cbor` to Pekko's `JacksonCborSerializer`, and the
   `serialization-bindings` block maps our marker trait to that serializer.
   Any class extending `CborSerializable` will be serialized with Jackson
   CBOR.

3. **The Scala module is loaded.** Jackson needs `DefaultScalaModule` to
   handle Scala-specific types: `Option`, `List`, case classes, sealed
   traits. Without it, Jackson would not know how to serialize most of
   our domain types.

### Why CBOR?

CBOR (Concise Binary Object Representation, RFC 8949) is a binary encoding
format that maps directly to the JSON data model. It supports all the same
types as JSON (objects, arrays, strings, numbers, booleans, null) but
encodes them as compact binary rather than text.

For an event journal, compactness matters. Events are written frequently
and stored indefinitely. The journal table grows with every state transition
across every aggregate. CBOR typically produces payloads 30 to 50 percent
smaller than equivalent JSON, which translates directly to less storage,
less I/O, and faster recovery when replaying events.

CBOR also preserves the ability to inspect data when needed. Tools like
`cbor-diag` can convert CBOR to human-readable form, and Jackson can
deserialize the same bytes to JSON for debugging. We get the compactness of
a binary format without the opacity of Protobuf or Avro.

@:callout(info)

Pekko also offers `JacksonJsonSerializer` for human-readable
persistence. If you prefer readable journal entries over compactness
(for example, during early development), switching is a one-line
configuration change. The serialization binding stays the same; only the
serializer class name changes.

@:@


### Polymorphic Snapshot Deserialization

Here is the trickiest part of the serialization story. Every event-sourced
actor has a `State` type that wraps the domain aggregate:

```scala
sealed trait State extends CborSerializable
case object EmptyState extends State
case class ActiveState(wave: Wave) extends State
```

The `ActiveState` holds a `Wave`, which is a sealed trait with multiple case
class subtypes: `Planned`, `Released`, `Completed`, `Cancelled`. When Pekko
takes a snapshot, it serializes the `ActiveState`, which includes the `Wave`
instance. When the actor recovers, Jackson must deserialize those bytes back
into the correct `Wave` subtype.

The problem: Jackson sees a field typed as `Wave` (the sealed trait) and does
not know which concrete case class to instantiate. Is it a `Wave.Planned`
with planning fields, or a `Wave.Released` with release fields?

The solution is the `@JsonTypeInfo` annotation on the sealed trait:

```scala
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait Wave:
  def id: WaveId

object Wave:
  case class Planned(...) extends Wave
  case class Released(...) extends Wave
  case class Completed(...) extends Wave
  case class Cancelled(...) extends Wave
```

<small>*File: wave/src/main/scala/neon/wave/Wave.scala*</small>

The `@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)` annotation tells Jackson to
include the fully qualified class name in the serialized form. When
serializing a `Wave.Released` instance, Jackson writes something like:

```json
{"@class": "neon.wave.Wave$Released", "id": "...", ...}
```

During deserialization, Jackson reads the `@class` field first, loads the
class, and then deserializes the remaining fields into that specific type.

Every event-sourced aggregate in Neon WES carries this annotation:

- `Wave`, `Task`, `ConsolidationGroup`, `TransportOrder`
- `HandlingUnit`, `Workstation`, `Slot`, `Inventory`
- `StockPosition`, `HandlingUnitStock`, `InboundDelivery`
- `GoodsReceipt`, `CycleCount`, `CountTask`

@:callout(info)

Forgetting `@JsonTypeInfo` on a polymorphic snapshot type is a
common source of production bugs. The actor will persist events and
snapshots without error, but when it tries to recover from a snapshot,
deserialization will fail because Jackson cannot determine which subtype
to create. The failure appears only on recovery, which might be hours or
days after the snapshot was written. Always add this annotation when
creating a new aggregate sealed trait.

@:@

### What About Events?

Events do not need `@JsonTypeInfo` because they are stored differently.
Each event is persisted with its own class manifest in the journal table.
Pekko writes the fully qualified class name alongside the serialized bytes,
so Jackson always knows the exact type to deserialize. The polymorphism
problem only arises with snapshots, where a single serialized blob contains
a field typed as a sealed trait.

### What Types Need CborSerializable?

Here is the complete list of types that must extend `CborSerializable`:

- **Commands:** `WaveActor.Command`, `TaskActor.Command`, and so on.
  These are the messages sent to actors via cluster sharding.
- **Responses:** `WaveActor.ReleaseResponse`, `TaskActor.CompleteResponse`,
  and so on. These are the reply messages sent back from actors.
- **State wrappers:** `WaveActor.State` (including `EmptyState` and
  `ActiveState`). These are serialized when taking snapshots.
- **Event types:** `WaveEvent`, `TaskEvent`, and so on. These are
  persisted to the journal.

All four categories must be serializable because all four pass through
Pekko's serialization boundary at some point during the actor lifecycle.


## Circe JSON for the HTTP API

The HTTP layer uses a different serializer for different reasons. HTTP
clients expect JSON. They need human-readable responses, not binary blobs.
And the serialization requirements are simpler: request and response DTOs
are plain case classes, never polymorphic sealed traits.

### Derives-Based Codec Generation

Neon WES uses Circe with Scala 3 derives clauses for automatic codec
generation:

```scala
case class CompleteTaskRequest(
    actualQuantity: Int,
    verified: Boolean
) derives Decoder

case class TaskCompletionResponse(
    status: String,
    taskId: String,
    actualQuantity: Int,
    requestedQuantity: Int,
    hasShortpick: Boolean,
    hasTransportOrder: Boolean
) derives Encoder.AsObject
```

<small>*File: app/src/main/scala/neon/app/http/TaskRoutes.scala*</small>

Request types derive `Decoder` (they are read from JSON). Response types
derive `Encoder.AsObject` (they are written to JSON). The `derives` keyword
tells the Scala 3 compiler to generate the codec implementation at compile
time. No reflection, no runtime overhead, no registration.

### The CirceSupport Bridge

Pekko HTTP has its own marshalling system that is independent of any JSON
library. The `CirceSupport` object bridges the two:

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

Two things to notice:

1. **`dropNullValues = true`.** When a field is `None` or `null`, it is
   omitted from the JSON output entirely rather than appearing as
   `"field": null`. This keeps responses clean and reduces payload size.

2. **Given instances, not imports.** Routes import `CirceSupport.given` to
   bring the marshallers into scope. Any type with a Circe `Encoder` can
   be returned from a route, and any type with a `Decoder` can be
   extracted from a request body. The Pekko HTTP directives `complete(...)` and
   `entity(as[...])` find the marshallers through Scala's given resolution.


## Schema Evolution

Both the persistence layer and the HTTP layer must handle schema evolution:
what happens when you add a field, remove a field, or change a type?

### Adding a Field

Jackson handles added fields gracefully. When deserializing an old event or
snapshot that lacks a field present in the current class definition, Jackson
uses the Scala default value if one exists, or `null` for reference types.
For case classes, this means:

```scala
// Version 1
case class TaskCompleted(taskId: TaskId, actualQuantity: Int)

// Version 2: added 'verified' with a default
case class TaskCompleted(
    taskId: TaskId,
    actualQuantity: Int,
    verified: Boolean = false
)
```

Old events without the `verified` field will deserialize with
`verified = false`. No migration needed. The same pattern works on the HTTP
side with Circe: fields with defaults are treated as optional during
decoding.

@:callout(info)

Always provide sensible defaults when adding fields to event
types. Events in the journal are immutable. You cannot update old events
to include the new field. The default value must represent what the
"absence" of that field means in business terms.

@:@

### Removing a Field

Jackson ignores unknown fields by default. If an old event contains a field
that no longer exists in the current class definition, Jackson simply skips
it during deserialization. No error, no data loss (the old data is still in
the journal, just not mapped to a class field).

This makes field removal safe for persistence. Remove the field from the
case class, and old events continue to deserialize without error.

### Renaming a Field

Renaming is the most dangerous evolution. Jackson matches fields by name.
If you rename `quantity` to `actualQuantity`, old events with `quantity`
will deserialize with `actualQuantity = 0` (or whatever the default is).
The `quantity` value in the old event is ignored as an unknown field.

For persistence, avoid renames. If you must rename, use Jackson's
`@JsonAlias` annotation to accept both the old and new names:

```scala
case class TaskCompleted(
    taskId: TaskId,
    @JsonAlias(Array("quantity"))
    actualQuantity: Int
)
```

### Event Versioning Strategy

For more complex evolution (changing the structure of an event, splitting
one event into two, merging events), Pekko provides event adapters. An
event adapter transforms old event formats to new ones during replay:

```scala
class TaskEventAdapter extends EventAdapter[TaskEvent]:
  override def manifest(event: TaskEvent): String = "v2"
  override def fromJournal(
      event: TaskEvent, manifest: String
  ): EventSeq[TaskEvent] =
    manifest match
      case "v1" => adaptV1ToV2(event)
      case "v2" => EventSeq.single(event)
```

Neon WES has not needed event adapters yet, because the event schemas have
remained stable. But the mechanism exists for when they do change. The key
principle is that old events are never modified in the journal; adapters
transform them on read.


## The Two Serializers Compared

| Concern                | Jackson CBOR (Persistence)         | Circe JSON (HTTP)              |
|------------------------|------------------------------------|--------------------------------|
| Format                 | Binary (CBOR)                      | Text (JSON)                    |
| Configuration          | `application.conf` binding         | `derives` on case classes      |
| Polymorphism           | `@JsonTypeInfo` annotation         | Not needed (flat DTOs)         |
| Null handling          | Preserved                          | `dropNullValues = true`        |
| Schema evolution       | Jackson defaults + event adapters  | Decoder defaults               |
| Scope                  | Commands, events, snapshots, state | HTTP request/response bodies   |
| Integration            | Pekko Serialization                | Pekko HTTP Marshalling         |


## Summary

- **CborSerializable** is a marker trait that maps types to Jackson CBOR via
  `application.conf`. All commands, events, responses, and state wrappers
  must extend it.
- **Java serialization is disabled.** Missing serializer bindings fail at
  startup, not silently at runtime.
- **CBOR** provides compact binary encoding, reducing journal storage and
  improving recovery speed.
- **`@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)`** on aggregate sealed traits
  enables polymorphic snapshot deserialization. Without it, actors cannot
  recover from snapshots.
- **Circe** handles HTTP JSON with compile-time `derives` codecs and
  `dropNullValues` for clean responses.
- **Schema evolution** relies on Jackson's default handling for added and
  removed fields, `@JsonAlias` for renames, and event adapters for
  structural changes.
