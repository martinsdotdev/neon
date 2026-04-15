# The Common Foundation

Every module in Neon WES depends on the `common` module. It provides the
shared vocabulary: identity types that prevent you from mixing up a `WaveId`
with a `TaskId`, enums that name concepts like priority and packaging level,
and utility types that encode warehouse knowledge about lots and units of
measure. Let's explore what lives here and why each type exists.

## The Problem of Primitive Types

Imagine a function that assigns a warehouse task to a user:

```scala
def assignTaskToUser(taskId: UUID, userId: UUID): Unit
```

Both parameters are `UUID`s. The compiler is perfectly happy if you call this
with the arguments reversed:

```scala
// Compiles without complaint, fails silently at runtime.
assignTaskToUser(userId, taskId)
```

This is the _primitive obsession_ problem. When you represent distinct domain
concepts with the same underlying type, the compiler cannot help you keep them
apart. The bug above will not produce a compile error, a warning, or even an
exception. It will silently assign the wrong task to the wrong user, and you
will discover the mistake in production when a worker's RF scanner shows
someone else's picks.

In a system with over twenty distinct identifier types, this is not a
theoretical concern. We need the compiler to reject these mistakes at the call
site, before the code ever runs.

## Opaque Type IDs

Scala 3 provides a feature called _opaque types_ that solves this problem with
zero runtime overhead. An opaque type is a compile-time alias that the compiler
treats as a distinct type, but at runtime the JVM sees the underlying type
directly. No wrapper object, no boxing, no extra allocation.

Here is how Neon WES defines `WaveId`:

```scala
package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type WaveId = UUID

object WaveId:
  def apply(): WaveId         = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): WaveId = value

  extension (id: WaveId) def value: UUID = id
```

<small>_File: common/src/main/scala/neon/common/WaveId.scala_</small>

Let's walk through each piece.

**The opaque type declaration.** `opaque type WaveId = UUID` tells the compiler
that `WaveId` is a `UUID` inside this file, but everywhere else it is its own
distinct type. Outside of the `WaveId` companion object, you cannot treat a
`WaveId` as a `UUID` or vice versa without an explicit conversion.

**The two `apply` overloads.** `apply()` generates a fresh identifier.
`apply(value: UUID)` wraps an existing `UUID` as a `WaveId`, which you need
when reconstructing identifiers from a database or a serialized message.

**The `extension` for `.value`.** Since the outside world cannot see that
`WaveId` is a `UUID`, we provide an extension method that returns the
underlying value. This is the escape hatch for when you genuinely need the raw
`UUID`, for example when binding a query parameter to a SQL statement.

Now our function signature becomes:

```scala
def assignTaskToUser(taskId: TaskId, userId: UserId): Unit
```

And the accidental swap becomes a compile error:

```scala
// Does not compile: found UserId, required TaskId.
assignTaskToUser(userId, taskId)
```

The compiler catches the bug instantly. At runtime, both `TaskId` and `UserId`
are still plain `UUID`s with no wrapper overhead. We get the safety of distinct
types for free.

### Why UUID v7?

You may have noticed that the `apply()` factory calls
`UuidCreator.getTimeOrderedEpoch()` rather than `UUID.randomUUID()`. This
generates a _UUID v7_, which encodes the current timestamp in its most
significant bits.

UUID v7 matters for two practical reasons:

1. **Database index performance.** B-tree indexes perform best when new values
   are approximately sequential. Random UUID v4 values scatter inserts across
   the entire index, causing page splits and write amplification. UUID v7
   values are monotonically increasing, so new rows land near the end of the
   index. For a system that creates thousands of tasks per hour, this
   difference is significant.

2. **Natural ordering.** Because the timestamp is embedded in the ID, you can
   sort entities by their identifier to get approximate creation order. This
   is useful for debugging, for "most recent first" projection queries, and
   for Pekko's event journal.

### The Full ID Catalogue

Every identifier in Neon WES follows the exact same pattern as `WaveId`. Here
is the complete list, grouped by the domain area they belong to:

**Outbound execution:**
`WaveId`, `TaskId`, `OrderId`, `ConsolidationGroupId`

**Physical inventory:**
`HandlingUnitId`, `HandlingUnitStockId`, `InventoryId`, `StockPositionId`,
`ContainerId`

**Warehouse movement:**
`TransportOrderId`, `WorkstationId`, `SlotId`, `SlotCode`

**Warehouse topology:**
`LocationId`, `WarehouseAreaId`, `ZoneId`, `CarrierId`

**Inbound and quality:**
`InboundDeliveryId`, `GoodsReceiptId`, `CycleCountId`, `CountTaskId`

**Master data:**
`SkuId`, `UserId`

@:callout(info)

Most of these are `opaque type X = UUID`, but two break the
pattern. `Lot` is `opaque type Lot = String` because lot codes are
human-readable alphanumeric strings, not UUIDs. `SlotCode` is also
`opaque type SlotCode = String` because slot positions within a container
are identified by short codes like `"A1"` or `"B3"`, not generated
identifiers. Both include `require` preconditions to reject empty strings.

@:@

The consistency is deliberate. When you add a new aggregate, you create a new
opaque type file following the same three-line structure: the type alias, the
companion with two `apply` overloads, and the `.value` extension.

## Shared Enums

Beyond identifiers, the `common` module defines enums for categorical domain
concepts. These appear in aggregate constructors, event payloads, and service
parameters across the entire system.

### Priority

```scala
enum Priority:
  case Low
  case Normal
  case High
  case Critical
```

<small>_File: common/src/main/scala/neon/common/Priority.scala_</small>

_Priority_ determines the urgency of orders and tasks. A `Normal` order follows
the standard warehouse workflow. A `High` order is expedited for same-day
delivery promises. A `Critical` order jumps to the front of every queue, for
situations like VIP escalations or regulatory recall shipments. During wave
planning, priority influences which orders get released first. During task
allocation, it determines which tasks workers receive before others.

### PackagingLevel

```scala
enum PackagingLevel:
  case Pallet
  case Case
  case InnerPack
  case Each
```

<small>_File: common/src/main/scala/neon/common/PackagingLevel.scala_</small>

_PackagingLevel_ models the GS1 packaging hierarchy from Chapter 1. A pallet
contains cases, a case contains inner packs, and an inner pack contains
individual eaches. This enum appears wherever the system reasons about
packaging: in handling units, inventory records, and order lines.

The hierarchy runs from coarsest (`Pallet`) to finest (`Each`). When the
system needs to convert between levels, it uses the `UomHierarchy` type that
we will see shortly.

### InventoryStatus

```scala
enum InventoryStatus:
  case Available
  case QualityHold
  case Damaged
  case Blocked
  case Expired
```

<small>_File: common/src/main/scala/neon/common/InventoryStatus.scala_</small>

_InventoryStatus_ represents the disposition of stock. Only `Available` stock
can be allocated to outbound orders. The other statuses represent restrictions:
`QualityHold` (awaiting inspection), `Damaged` (physically harmed), `Blocked`
(administrative holds such as regulatory recalls), and `Expired` (past shelf
life). These categories align with the ISA-95 material model and SAP EWM
stock types. When the allocation service searches for inventory to fulfill an
order, it filters on `InventoryStatus.Available` before anything else.

### AllocationStrategy

```scala
enum AllocationStrategy:
  case Fefo
  case Fifo
  case NearestLocation
```

<small>_File: common/src/main/scala/neon/common/AllocationStrategy.scala_</small>

_AllocationStrategy_ controls how the system selects inventory positions when
fulfilling demand. We discussed FEFO and FIFO in Chapter 1; here they become
concrete enum values:

- `Fefo` (First Expired, First Out) sorts by expiration date ascending.
  Mandatory for pharmaceutical and food warehouses under FDA and EU GDP.
- `Fifo` (First In, First Out) sorts by production date ascending. The
  standard approach for non-perishable goods under GAAP (ASC 330).
- `NearestLocation` minimizes pick travel distance by selecting the closest
  available stock.

A strategy is typically configured per SKU or SKU category. A pharmaceutical
warehouse might use `Fefo` for medications and `NearestLocation` for packaging
materials.

### StockLockType

```scala
enum StockLockType:
  case Outbound
  case Inbound
  case InternalMove
  case Count
  case Adjustment
```

<small>_File: common/src/main/scala/neon/common/StockLockType.scala_</small>

When stock is reserved for an operation but not yet physically moved,
_StockLockType_ records the reason. `Outbound` locks route to the allocated
quantity bucket (stock promised to a specific order). All other lock types
route to the reserved quantity bucket (stock temporarily held for an internal
process).

### Other Shared Enums

The `common` module contains several additional enums that we will meet in
later chapters:

- `CountMethod` (`Blind`, `Informed`) and `CountType` (`Planned`, `Random`,
  `Triggered`, `Recount`) support the cycle count subsystem.
- `WorkstationMode` (`Receiving`, `Picking`, `Counting`, `Relocation`)
  determines what type of work a workstation processes.
- `AdjustmentReasonCode` provides a detailed taxonomy of inventory adjustment
  reasons, required for SOX Section 404 audit trails.
- `Role` (`Admin`, `Supervisor`, `Operator`, `Viewer`) and `Permission`
  define the authorization model. Each `Permission` carries a string key like
  `"wave:plan"` or `"task:complete"` that maps to a specific operation.

All of these live in `common` because they are referenced by multiple modules.

## Utility Types

### LotAttributes

Warehouses that handle perishable goods or serialized products need to track
more than just "how many units are here." They need the batch number, the
manufacture date, the expiration date, and sometimes the serial number. The
`LotAttributes` case class captures all of this:

```scala
case class LotAttributes(
    lot: Option[Lot] = None,
    expirationDate: Option[LocalDate] = None,
    productionDate: Option[LocalDate] = None,
    serialNumber: Option[String] = None
):

  def remainingShelfLifeDays(referenceDate: LocalDate): Int =
    expirationDate match
      case None       => Int.MaxValue
      case Some(date) =>
        val days = ChronoUnit.DAYS.between(referenceDate, date).toInt
        if days < 0 then 0 else days

  def isExpired(referenceDate: LocalDate): Boolean =
    expirationDate.exists(date => !referenceDate.isBefore(date))
```

<small>_File: common/src/main/scala/neon/common/LotAttributes.scala_</small>

Each field maps to a GS1 Application Identifier (AI):

| Field            | GS1 AI | Meaning                    |
| :--------------- | :----- | :------------------------- |
| `lot`            | AI 10  | Batch or lot number        |
| `expirationDate` | AI 17  | Shelf life expiration date |
| `productionDate` | AI 11  | Date of manufacture        |
| `serialNumber`   | AI 21  | Item-level serial number   |

All fields are `Option`s because not every product requires every attribute. A
bag of bolts has no expiration date. A non-serialized consumer product has no
serial number. A warehouse that does not track lots at all simply leaves every
field as `None`.

The two methods support the FEFO allocation strategy.
`remainingShelfLifeDays` computes how many days of shelf life remain from a
reference date, returning `Int.MaxValue` for non-perishable stock. `isExpired`
returns `true` if stock is at or past its expiration date. Together, they let
the allocation service sort candidates by remaining shelf life and exclude
expired stock in a single pass.

### UomHierarchy

When a customer orders "2 cases" of a product, the system needs to know how
many individual eaches that represents. This conversion is what
_UomHierarchy_ (Unit-of-Measure Hierarchy) encodes:

```scala
opaque type UomHierarchy = Map[PackagingLevel, Int]

object UomHierarchy:

  val empty: UomHierarchy = Map.empty

  def apply(entries: (PackagingLevel, Int)*): UomHierarchy =
    val m = Map(entries*)
    require(
      !m.contains(PackagingLevel.Each),
      "Each is the implicit base unit and must not appear in the hierarchy"
    )
    require(m.values.forall(_ > 0), "all eaches-per-unit values must be positive")
    m

  extension (h: UomHierarchy)
    def get(level: PackagingLevel): Option[Int] = h.get(level)
    def apply(level: PackagingLevel): Int       = h(level)
    def contains(level: PackagingLevel): Boolean = h.contains(level)
    def nonEmpty: Boolean = h.nonEmpty
    def isEmpty: Boolean  = h.isEmpty
```

<small>_File: common/src/main/scala/neon/common/UomHierarchy.scala_</small>

`UomHierarchy` is another opaque type, wrapping a `Map[PackagingLevel, Int]`.
Each entry maps a packaging level to the number of eaches it contains. The
`Each` level is the implicit base unit and must not appear as a key.

Here is a concrete example. A product where one pallet holds 48 eaches and one
case holds 12 eaches:

```scala
val hierarchy = UomHierarchy(
  PackagingLevel.Pallet -> 48,
  PackagingLevel.Case   -> 12
)
```

Now `hierarchy(PackagingLevel.Case)` returns `12`, meaning one case equals 12
eaches. During wave planning, the `UomExpansion` module in the `wave` project
uses these hierarchies to convert order line quantities into eaches, the
common denomination used for inventory tracking and task creation.

The `require` preconditions enforce two invariants: `Each` cannot appear in the
map (it is always implicitly 1), and every conversion factor must be positive.
These checks catch configuration errors at construction time rather than
letting invalid data propagate through the system.

## The CborSerializable Marker Trait

In the `serialization` subpackage lives a trait with no methods at all:

```scala
package neon.common.serialization

trait CborSerializable
```

<small>_File: common/src/main/scala/neon/common/serialization/CborSerializable.scala_</small>

This _marker trait_ serves a single purpose: it binds types to the Jackson CBOR
serializer in Pekko's configuration. Every actor command, every command
response, every state wrapper, and every event envelope in the system extends
`CborSerializable`. When Pekko needs to serialize one of these types (to
persist an event, to send a message across the cluster, or to take a snapshot),
it looks up the serializer binding and finds Jackson CBOR.

We will see `extends CborSerializable` on dozens of types in the chapters
ahead. For now, know that it exists and that it lives in `common` so every
module can reference it. Chapter 17 covers Pekko serialization in depth.

## R2dbcProjectionQueries

The final shared piece in `common` is the `R2dbcProjectionQueries` trait:

```scala
trait R2dbcProjectionQueries:

  protected def connectionFactory: ConnectionFactory
  protected given system: ActorSystem[?]
  protected given ec: ExecutionContext

  protected def queryProjectionIds(
      sql: String,
      param: Any,
      idColumn: String
  ): Future[List[UUID]]
```

<small>_File: common/src/main/scala/neon/common/R2dbcProjectionQueries.scala_</small>

This trait provides helper methods for querying CQRS read-side projection
tables via raw R2DBC and Pekko Streams. Several `PekkoXxxRepository`
implementations need to answer the same question: "give me all entity IDs
matching some criteria in a projection table, so I can fan out commands to
individual actors." For example, finding all task IDs belonging to a wave so
that each task actor can receive a cancellation command.

We will see `R2dbcProjectionQueries` mixed into Pekko repository
implementations in Chapters 11 and 12.

## What Comes Next

With the common types in hand, we are ready to build domain aggregates. In the
next chapter, we will learn how Neon WES models state machines at compile time
using a pattern called _typestate encoding_, turning invalid state transitions
into compile errors.
