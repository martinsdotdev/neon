# Stock Management

Every warehouse system must answer a deceptively simple question: _how much
of each product do we have?_ The deceptive part is that "have" means different
things in different contexts. The picker needs to know what is physically on a
shelf. The wave planner needs to know what is available for new orders. The
auditor needs to know what has been committed, reserved, or placed on hold.

In this chapter, we will explore how Neon WES tracks stock at two levels of
granularity, how the 4-bucket quantity model prevents over-allocation while
supporting concurrent operations, and how stock flows through the system from
allocation to consumption. We will also look at allocation strategies for
selecting which stock to pick first and the SOX-compliant adjustment process
that closes the loop after cycle counts.

## Two Aggregates, Two Granularities

Neon WES represents inventory with two distinct aggregates, each serving a
different set of operations.

**Inventory** is the location-level view. It tracks on-hand and reserved
quantities for a specific (location, SKU, lot) triad. Think of it as the
answer to "what is in bin A-03-02 right now?" Inventory supports the
reserve/release/consume lifecycle that individual tasks need when they interact
with a specific shelf position.

```scala
case class Inventory private[inventory] (
    id: InventoryId,
    locationId: LocationId,
    skuId: SkuId,
    packagingLevel: PackagingLevel,
    lot: Option[Lot],
    onHand: Int,
    reserved: Int
):
  def available: Int = onHand - reserved
```

<small>_File: inventory/src/main/scala/neon/inventory/Inventory.scala_</small>

**StockPosition** is the area-level view. It tracks on-hand stock decomposed
into four buckets for a (SKU, warehouse area, lot attributes) combination.
Think of it as the answer to "across the entire ambient zone, how much of
SKU-4472 is available, allocated, reserved, and blocked?" StockPosition is the
aggregate that wave planning, allocation, and adjustment operations work with.

```scala
case class StockPosition private[stockposition] (
    id: StockPositionId,
    skuId: SkuId,
    warehouseAreaId: WarehouseAreaId,
    lotAttributes: LotAttributes,
    status: InventoryStatus,
    onHandQuantity: Int,
    availableQuantity: Int,
    allocatedQuantity: Int,
    reservedQuantity: Int,
    blockedQuantity: Int
)
```

<small>_File: stock-position/src/main/scala/neon/stockposition/StockPosition.scala_</small>

Why two aggregates instead of one? Because they serve fundamentally different
query patterns:

| Concern           | Inventory (location-level)   | StockPosition (area-level)     |
| ----------------- | ---------------------------- | ------------------------------ |
| Key               | (location, SKU, lot)         | (SKU, warehouse area, lot)     |
| Primary consumer  | Task execution               | Wave planning, allocation      |
| Reservation model | Simple (on-hand vs reserved) | 4-bucket decomposition         |
| Lot tracking      | Optional `Lot` value         | Full `LotAttributes` with FEFO |

Inventory tells a picker _where_ to go. StockPosition tells the planner _how
much_ is available across an entire zone.

## The 4-Bucket Quantity Model

The heart of StockPosition is its four-bucket decomposition. Every unit of
on-hand stock falls into exactly one bucket:

```
onHand = available + allocated + reserved + blocked
```

This is not a suggestion. It is an enforced invariant:

```scala
private def validateInvariant(): Unit =
  require(onHandQuantity >= 0, ...)
  require(availableQuantity >= 0, ...)
  require(allocatedQuantity >= 0, ...)
  require(reservedQuantity >= 0, ...)
  require(blockedQuantity >= 0, ...)
  require(
    onHandQuantity == availableQuantity + allocatedQuantity
      + reservedQuantity + blockedQuantity,
    ...
  )
```

<small>_File: stock-position/src/main/scala/neon/stockposition/StockPosition.scala_</small>

Every mutation method calls `validateInvariant()` after computing the new
state. If any bucket goes negative or the sum diverges from on-hand, the
operation fails immediately. Let's look at what each bucket represents:

- **Available**: stock that can be allocated to new outbound orders. This is the
  "free" pool that wave planning draws from.
- **Allocated**: stock committed to outbound orders. It has been promised to a
  wave but not yet physically picked.
- **Reserved**: stock locked for internal operations such as cycle counting,
  relocation, or receiving holds. It is spoken for but not leaving the building.
- **Blocked**: stock placed on administrative hold. Recalls, quality issues, or
  regulatory freezes move stock here. It cannot be allocated, reserved, or
  picked until explicitly unblocked.

@:callout(info)

All four buckets must be non-negative at all times. This means you
cannot allocate more than is available, cannot consume more than is allocated,
and cannot block what has already been promised elsewhere. The invariant check
enforces these constraints after every operation.

@:@

## Operations on StockPosition

StockPosition exposes a rich set of operations, each moving quantities between
specific buckets. Every operation returns a `(StockPosition, Event)` tuple,
following the same typestate pattern we saw in earlier chapters.

### Allocate and Deallocate

Allocation moves quantity from the available bucket to the allocated bucket.
This happens during wave release when the system commits stock to outbound
orders:

```scala
def allocate(quantity: Int, at: Instant): (StockPosition, StockPositionEvent.Allocated) =
  require(quantity > 0, ...)
  require(quantity <= availableQuantity, ...)
  val updated = copy(
    availableQuantity = availableQuantity - quantity,
    allocatedQuantity = allocatedQuantity + quantity
  )
  updated.validateInvariant()
  val event = StockPositionEvent.Allocated(id, quantity, at)
  (updated, event)
```

<small>_File: stock-position/src/main/scala/neon/stockposition/StockPosition.scala_</small>

Deallocation is the reverse: when a task is cancelled, `deallocate` moves
quantity from allocated back to available.

### Consume Allocated

When a picker completes a task, the allocated stock is consumed. This is the
only operation that reduces `onHandQuantity`, because the items are physically
leaving the storage area:

```scala
def consumeAllocated(
    quantity: Int,
    at: Instant
): (StockPosition, StockPositionEvent.AllocatedConsumed) =
  require(quantity > 0, ...)
  require(quantity <= allocatedQuantity, ...)
  val updated = copy(
    onHandQuantity = onHandQuantity - quantity,
    allocatedQuantity = allocatedQuantity - quantity
  )
  updated.validateInvariant()
  (updated, StockPositionEvent.AllocatedConsumed(id, quantity, at))
```

Notice the asymmetry: `allocate` does not change `onHandQuantity` (the stock is
still physically present), but `consumeAllocated` reduces both `onHandQuantity`
and `allocatedQuantity` (the stock has left the shelf).

### Reserve and Release Reservation

Reservations are similar to allocations but serve internal operations. The
`lockType` parameter distinguishes the purpose:

```scala
def reserve(
    quantity: Int,
    lockType: StockLockType,
    at: Instant
): (StockPosition, StockPositionEvent.Reserved) =
  require(quantity > 0, ...)
  require(quantity <= availableQuantity, ...)
  val updated = copy(
    availableQuantity = availableQuantity - quantity,
    reservedQuantity = reservedQuantity + quantity
  )
  updated.validateInvariant()
  (updated, StockPositionEvent.Reserved(id, quantity, lockType, at))
```

<small>_File: stock-position/src/main/scala/neon/stockposition/StockPosition.scala_</small>

`StockLockType` captures why the reservation exists: `Inbound` for receiving
holds, `InternalMove` for relocations, `Count` for cycle counting, or
`Adjustment` for correction holds. When the operation completes,
`releaseReservation` moves the quantity back to available.

### Block and Unblock

Blocking is an administrative action that removes stock from the available pool
without committing it to any specific operation:

```scala
def block(quantity: Int, at: Instant): (StockPosition, StockPositionEvent.Blocked) =
  require(quantity > 0, ...)
  require(quantity <= availableQuantity, ...)
  val updated = copy(
    availableQuantity = availableQuantity - quantity,
    blockedQuantity = blockedQuantity + quantity
  )
  updated.validateInvariant()
  (updated, StockPositionEvent.Blocked(id, quantity, at))
```

A product recall, a quality inspection hold, or a regulatory freeze would use
`block`. When the issue is resolved, `unblock` returns the stock to available.

### Add Quantity and Adjust

Inbound receiving calls `addQuantity`, which increases both `onHandQuantity`
and `availableQuantity`. New stock always enters the available bucket. If it
needs to be held for inspection, a subsequent `reserve` or `block` call moves
it to the appropriate bucket.

Adjustments handle corrections discovered through cycle counting or other
audits. Unlike other operations, the `adjust` delta can be negative:

```scala
def adjust(
    delta: Int,
    reasonCode: AdjustmentReasonCode,
    at: Instant
): (StockPosition, StockPositionEvent.Adjusted) =
  val updated = copy(
    onHandQuantity = onHandQuantity + delta,
    availableQuantity = availableQuantity + delta
  )
  updated.validateInvariant()
  (updated, StockPositionEvent.Adjusted(id, delta, reasonCode, at))
```

<small>_File: stock-position/src/main/scala/neon/stockposition/StockPosition.scala_</small>

The `reasonCode` is mandatory. Every adjustment must declare _why_ it happened.
We will return to this requirement in the SOX compliance section below.

## Allocation Strategies

When a wave is released, the system needs to decide _which_ stock positions to
draw from for each SKU. This is the job of `StockAllocationPolicy`, a stateless
policy that implements three strategies:

```scala
object StockAllocationPolicy:

  def apply(
      requests: List[AllocationRequest],
      availableStock: Map[SkuId, List[StockPosition]],
      strategy: AllocationStrategy,
      referenceDate: LocalDate,
      minimumShelfLifeDays: Int = 0
  ): Either[StockAllocationError, List[AllocationResult]]
```

<small>_File: core/src/main/scala/neon/core/StockAllocationPolicy.scala_</small>

### FEFO (First Expired, First Out)

Mandatory for pharmaceuticals (FDA 21 CFR 211) and food (EU GDP, FSMA). FEFO
sorts stock positions by expiration date ascending, then production date
ascending, then available quantity ascending. The earliest-expiring stock gets
picked first:

```scala
case AllocationStrategy.Fefo =>
  positions.sortBy(sp =>
    (
      sp.lotAttributes.expirationDate.getOrElse(LocalDate.MAX),
      sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
      sp.availableQuantity
    )
  )
```

### FIFO (First In, First Out)

The GAAP-preferred strategy (ASC 330) for general merchandise. FIFO sorts by
production date ascending, then available quantity ascending. The oldest stock
gets picked first:

```scala
case AllocationStrategy.Fifo =>
  positions.sortBy(sp =>
    (
      sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
      sp.availableQuantity
    )
  )
```

### NearestLocation

Minimizes pick travel distance by selecting the closest stock positions. This
strategy is a placeholder in the current implementation, reserved for future
integration with warehouse layout data.

### Greedy First-Fit with Shelf Life Filtering

Regardless of strategy, the policy uses greedy first-fit allocation. After
sorting stock positions according to the strategy, it walks the list and takes
as much as possible from each position until the request is fulfilled:

```scala
private def greedyAllocate(
    sorted: List[StockPosition],
    needed: Int
): (List[StockAllocation], Int) =
  var remaining = needed
  val allocations = scala.collection.mutable.ListBuffer[StockAllocation]()
  for sp <- sorted if remaining > 0 do
    val qty = math.min(remaining, sp.availableQuantity)
    allocations += StockAllocation(sp.id, qty, sp.lotAttributes)
    remaining -= qty
  (allocations.toList, remaining)
```

<small>_File: core/src/main/scala/neon/core/StockAllocationPolicy.scala_</small>

Before sorting, the policy filters out positions with insufficient shelf life.
If `minimumShelfLifeDays` is set and no positions meet the threshold, the policy
returns an `InsufficientShelfLife` error rather than silently allocating
nearly-expired stock.

@:callout(info)

The allocation policy supports partial fulfillment. If a request
for 100 units can only be satisfied with 85, the `AllocationResult` will
contain a `shortQuantity` of 15. The caller decides whether to proceed with
the partial allocation or reject it entirely.

@:@

## Stock Consumption Patterns

The `TaskCompletionService` orchestrates what happens to allocated stock when a
task finishes. Three patterns emerge from the relationship between requested
and actual quantities:

### Full Pick

The picker picks exactly what was requested. The entire allocated quantity is
consumed:

```scala
else if completed.actualQuantity > 0 then
  // Full pick: consume all
  val (updated, event) = sp.consumeAllocated(completed.actualQuantity, at)
  spRepo.save(updated, event)
  Some((updated, event))
```

### Partial Pick (Shortpick)

The picker finds fewer items than expected. The actual quantity is consumed,
and the remainder is deallocated back to available:

```scala
if completed.actualQuantity > 0 && remainder > 0 then
  // Partial pick: consume actual, then deallocate remainder
  val (afterConsume, consumeEvent) =
    sp.consumeAllocated(completed.actualQuantity, at)
  spRepo.save(afterConsume, consumeEvent)
  val (afterDeallocate, deallocateEvent) =
    afterConsume.deallocate(remainder, at)
  spRepo.save(afterDeallocate, deallocateEvent)
  Some((afterDeallocate, deallocateEvent))
```

This is a two-step process. First, `consumeAllocated` reduces both on-hand and
allocated by the actual amount picked. Then, `deallocate` moves the unfulfilled
remainder from allocated back to available, making it eligible for future waves.

### Zero Pick

The picker arrives at the location and finds nothing. The entire requested
quantity is deallocated:

```scala
else if completed.requestedQuantity > 0 then
  // Zero pick (full shortpick): deallocate all back to available
  val (updated, event) = sp.deallocate(completed.requestedQuantity, at)
  spRepo.save(updated, event)
  Some((updated, event))
```

<small>_File: core/src/main/scala/neon/core/TaskCompletionService.scala_</small>

@:callout(info)

In all three cases, the ShortpickPolicy runs separately to
determine whether a replacement task should be created for the unfulfilled
quantity. Stock consumption and shortpick creation are independent concerns.

@:@

## SOX-Compliant Adjustments

When cycle counting reveals a variance between expected and actual quantities
(we will cover cycle counting in the next chapter), the discrepancy must be
resolved through an inventory adjustment. In the United States, publicly traded
companies must comply with SOX Section 404, which requires internal controls
over financial reporting. Inventory is a balance sheet asset, so every
adjustment needs a paper trail.

Neon WES enforces two controls:

### Reason Codes

Every adjustment requires an `AdjustmentReasonCode`. The enum provides 13
values organized into four categories:

| Category    | Reason Codes                                                 |
| ----------- | ------------------------------------------------------------ |
| Shrinkage   | Damaged, Expired, Shrinkage                                  |
| Operational | CycleCountAdjustment, ReceivingDiscrepancy, Misplaced, Found |
| Quality     | QualityHold, QualityRelease, Defective                       |
| Business    | InternalUse, Disposal, DataCorrection                        |

The reason code travels with the `Adjusted` event into the event store,
creating an immutable audit trail that links every quantity change to a
specific business justification.

### Segregation of Duties

The person who counted the inventory must not be the same person who approves
the adjustment. The `AdjustmentService` enforces this:

```scala
object AdjustmentService:

  def adjust(
      variance: CountVariance,
      adjustedBy: UserId,
      reasonCode: AdjustmentReasonCode,
      at: Instant
  ): Either[AdjustmentError, AdjustmentResult] =
    if adjustedBy == variance.countedBy then
      Left(AdjustmentError.SegregationOfDutiesViolation(
        variance.countedBy, adjustedBy
      ))
    else Right(AdjustmentResult(variance, adjustedBy, reasonCode))
```

<small>_File: core/src/main/scala/neon/core/AdjustmentService.scala_</small>

If User A counted 47 units where the system expected 50, User A cannot also
approve the adjustment of -3. A different user (User B) must review the
variance and apply it with an appropriate reason code. This is not just good
practice; it is a regulatory requirement that auditors specifically test for.

@:callout(info)

The `SegregationOfDutiesViolation` error is a sealed trait case
class, not an exception. The caller can handle it precisely, perhaps by
prompting for a different approver rather than failing the entire workflow.

@:@

## The Full Stock Lifecycle

Let's trace a unit of stock through its complete lifecycle to see how all
these pieces connect:

1. **Inbound receiving** calls `addQuantity` on a StockPosition. On-hand and
   available both increase.
2. **Wave release** calls `allocate` through the StockAllocationPolicy. Stock
   moves from available to allocated.
3. **Task execution** (full pick) calls `consumeAllocated`. On-hand and
   allocated both decrease. The stock has left the building.
4. **Task cancellation** would call `deallocate` instead, moving stock from
   allocated back to available.
5. **Cycle counting** may call `reserve` with `StockLockType.Count` to
   temporarily hold stock during verification, then `releaseReservation` when
   counting is complete.
6. **Adjustment** calls `adjust` with a delta and reason code to correct any
   variance found.
7. **Quality hold** calls `block` to remove stock from the available pool, then
   `unblock` when the hold is lifted.

Through all of these transitions, the 4-bucket invariant holds. The
`validateInvariant()` call at the end of every operation is the safety net that
prevents the system from entering an inconsistent state, even when multiple
operations interleave across concurrent waves and tasks.

In the next chapter, we will explore the inbound and cycle counting flows that
create and verify this stock data.
