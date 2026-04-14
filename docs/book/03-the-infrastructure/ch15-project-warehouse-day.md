# Project: A Day in the Warehouse

This is our second project chapter. In Chapter 9 we traced a single flow
through six layers. Now we will trace an entire warehouse shift, touching
every domain module in the system. The
`WarehouseDaySimulationSuite` is a 619-line integration test that
exercises inbound receiving, wave planning with FEFO allocation, picking
with shortpick handling, blind cycle counting, SOX-compliant inventory
adjustment, and workstation mode switching. All in one file, all sharing
the same in-memory repositories, all building on state that earlier steps
created.

Here is what we are applying:

1. **Opaque type IDs** (Chapter 3): `SkuId`, `WaveId`, `TaskId`,
   `CycleCountId`, `CountTaskId`, `StockPositionId`, `WorkstationId`,
   `UserId`, and more keep every identifier type-safe across module
   boundaries.
2. **Lot tracking** (Chapter 3): `LotAttributes` with `Lot`,
   `expirationDate`, and `productionDate` model GS1 AI-10/AI-17 pharma
   traceability requirements.
3. **Typestate transitions** (Chapter 4): `InboundDelivery.New` to
   `Receiving` to `Received`, `GoodsReceipt.Open` to `Confirmed`,
   `CycleCount.New` to `InProgress` to `Completed`, `CountTask.Pending`
   to `Assigned` to `Recorded`, `Workstation.Disabled` to `Idle` to
   `Active` and back.
4. **Events** (Chapter 5): `Created`, `QuantityAdded`, `Allocated`,
   `AllocatedConsumed`, `Deallocated`, `Adjusted` on stock positions.
   `TaskCreated`, `TaskCompleted` on tasks. `CycleCountStarted`,
   `CycleCountCompleted` on counts.
5. **Policies** (Chapter 6): `PutawayCreationPolicy`,
   `TaskCreationPolicy`, `StockAllocationPolicy`, `ShortpickPolicy`,
   `CountCreationPolicy` make pure decisions.
6. **Services** (Chapter 7): `InboundReceivingService`,
   `WaveReleaseService`, `TaskCompletionService`,
   `CountCompletionService`, `AdjustmentService` orchestrate cascades.
7. **Repositories** (Chapter 8): Seven in-memory repositories carry
   shared state across every `describe` block without any infrastructure.
8. **Stock position 4-bucket model** (Chapter 3): `onHandQuantity`,
   `availableQuantity`, `allocatedQuantity`, and friends track inventory
   through allocation, consumption, deallocation, and adjustment.
9. **Workstation typestate** (Chapter 4): Compile-time enforcement
   prevents mode switching on an `Active` workstation.

By the end of this chapter, we will have received goods at the dock,
allocated stock for outbound orders, picked with a shortpick, counted
inventory, adjusted a variance under SOX rules, and switched workstation
modes. Every line of code is real. Every assertion passes. The test is
the specification.


## The Test as Living Documentation

The `WarehouseDaySimulationSuite` lives in the `core` module because it
needs access to all domain modules and all core services. It extends
`AnyFunSpec` with `OptionValues` and `EitherValues`, giving us `.value`
accessors that fail with clear messages when an `Option` is `None` or
an `Either` is on the wrong side.

The key design decision: all seven repositories are instantiated once and
shared across every `describe` block. State carries forward. The stock
created at 06:00 is the stock allocated at 08:00, consumed at 10:00,
counted at 14:00, and adjusted at 16:00.

```scala
describe("A full day of warehouse operations"):

  // Shared state across the day
  val stockRepo = InMemoryStockPositionRepository()
  val taskRepo = InMemoryTaskRepository()
  val waveRepo = InMemoryWaveRepository()
  val cgRepo = InMemoryConsolidationGroupRepository()
  val toRepo = InMemoryTransportOrderRepository()
  val ccRepo = InMemoryCycleCountRepository()
  val ctRepo = InMemoryCountTaskRepository()
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Seven repositories. Seven mutable maps. Seven event buffers. That is the
entire infrastructure for a 12-hour warehouse simulation.

Time progression uses a simple helper:

```scala
val dayStart = Instant.parse("2027-01-15T06:00:00Z")
def at(hoursAfterStart: Int): Instant =
  dayStart.plusSeconds(hoursAfterStart * 3600L)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`at(0)` is 06:00. `at(2)` is 08:00. `at(4)` is 10:00. This convention
lets us reason about warehouse time without cluttering every call site
with `Instant.parse` strings.

The test also defines the warehouse topology (locations, containers),
three SKUs, four lot attribute sets, and four operators:

```scala
val aspirinSkuId = SkuId() // pharma: requires FEFO
val bandageSkuId = SkuId() // medical supply
val syringeSkuId = SkuId() // medical device

val receivingOperator = UserId()
val picker = UserId()
val countOperator = UserId()
val supervisor = UserId() // different from countOperator for SOX
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Four distinct `UserId` values. The separation between `countOperator`
and `supervisor` is not just documentation; it is a SOX compliance
requirement that we will enforce in code at 16:00.

Let's walk through the day.


## 06:00 -- Inbound Receiving

The first truck arrives at the dock. We are receiving aspirin (two lots
with different expiration dates) and bandages.

### Creating Stock Positions

Before the inbound delivery lifecycle begins, we create stock positions
for the received goods. Each position is keyed by (SKU, warehouse area,
lot attributes):

```scala
val (aspirinEarly, aspirinEarlyEvent) =
  StockPosition.create(
    aspirinSkuId, warehouseAreaId, aspirinLotEarlyExpiry, 200, at(0)
  )
stockRepo.save(aspirinEarly, aspirinEarlyEvent)

val (aspirinLate, aspirinLateEvent) =
  StockPosition.create(
    aspirinSkuId, warehouseAreaId, aspirinLotLateExpiry, 150, at(0)
  )
stockRepo.save(aspirinLate, aspirinLateEvent)

val (bandageStock, bandageEvent) =
  StockPosition.create(
    bandageSkuId, warehouseAreaId, bandageLot, 500, at(0)
  )
stockRepo.save(bandageStock, bandageEvent)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Three stock positions. Two for aspirin (same SKU, different lots) and
one for bandages. The lot attributes carry GS1 data:

```scala
val aspirinLotEarlyExpiry = LotAttributes(
  lot = Some(Lot("ASP-2026-A")),
  expirationDate = Some(LocalDate.of(2027, 3, 31)),
  productionDate = Some(LocalDate.of(2026, 1, 15))
)
val aspirinLotLateExpiry = LotAttributes(
  lot = Some(Lot("ASP-2026-B")),
  expirationDate = Some(LocalDate.of(2027, 9, 30)),
  productionDate = Some(LocalDate.of(2026, 4, 1))
)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Lot `ASP-2026-A` expires in March 2027. Lot `ASP-2026-B` expires in
September 2027. This six-month gap will matter at 08:00 when FEFO
allocation decides which lot to draw from first.

### InboundDelivery Lifecycle

The delivery aggregate walks through its typestate machine:

```scala
val delivery = InboundDelivery.New(
  InboundDeliveryId(), aspirinSkuId, PackagingLevel.Each,
  aspirinLotEarlyExpiry, 200
)
val (receiving, receivingEvent) = delivery.startReceiving(at(0))
val (afterReceive, receiveEvent) = receiving.receive(200, 0, at(0))
val (received, receivedEvent) = afterReceive.complete(at(0))
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Three transitions: `New` to `Receiving` to `Receiving` (with quantity)
to `Received`. Each call returns a `(NewState, Event)` tuple. The
`receive(200, 0, at(0))` call records 200 accepted and 0 rejected.
After that, `isFullyReceived` is true (200 received + 0 rejected equals
200 expected), so `complete` succeeds.

@:callout(info)

If `isFullyReceived` were false, `complete` would throw via
its `require` precondition. The alternative path is `close`, which
forces the remaining quantity as rejected. These are the two exits
from the `Receiving` state.

@:@

### GoodsReceipt Lifecycle

The goods receipt records what physically arrived:

```scala
val receipt = GoodsReceipt.Open(GoodsReceiptId(), delivery.id, Nil)
val receivedLine = ReceivedLine(
  aspirinSkuId, 200, PackagingLevel.Each,
  aspirinLotEarlyExpiry, Some(inboundPallet)
)
val (withLine, lineEvent) = receipt.recordLine(receivedLine, at(0))
val (confirmed, confirmedEvent) = withLine.confirm(at(0))
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`GoodsReceipt.Open` starts with an empty line list. `recordLine` appends
a `ReceivedLine` (the `require(line.quantity > 0)` guards positive
quantities). `confirm` requires at least one line. Both transitions
return `(NewState, Event)` tuples following the same typestate pattern.

### InboundReceivingService Orchestration

The service ties everything together:

```scala
val inboundService = InboundReceivingService(taskRepo, stockRepo)
val inboundResult = inboundService.processConfirmedReceipt(
  confirmed, OrderId(), at(0), Some(aspirinEarly.id)
)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`processConfirmedReceipt` does two things. First, it calls
`PutawayCreationPolicy` to create planned putaway tasks from the
receipt's received lines. Second, because we passed a `stockPositionId`,
it calls `sp.addQuantity(200, at)` to increase the early-expiry
position's on-hand and available quantities by 200.

The test verifies both outcomes:

```scala
it("creates putaway tasks from the receipt"):
  assert(inboundResult.tasks.nonEmpty)
  val (putawayTask, _) = inboundResult.tasks.head
  assert(putawayTask.taskType == TaskType.Putaway)

it("updates stock position with received quantity"):
  val addedEvents = stockRepo.events.collect {
    case e: StockPositionEvent.QuantityAdded => e
  }
  assert(addedEvents.exists(_.quantity == 200))
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

After inbound, the aspirin early-expiry position holds 400 on-hand
(200 original + 200 from the receipt). This is the starting inventory
for outbound allocation.


## 08:00 -- Wave Planning and Release

Two customer orders arrive. Order 1 wants 50 units of aspirin. Order 2
wants 30 units. Together they need 80 units, and we have 400 available
in the early-expiry lot and 150 in the late-expiry lot.

```scala
val order1 = Order(
  OrderId(), Priority.High,
  List(OrderLine(aspirinSkuId, PackagingLevel.Each, 50))
)
val order2 = Order(
  OrderId(), Priority.Normal,
  List(OrderLine(aspirinSkuId, PackagingLevel.Each, 30))
)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

### Planning

`WavePlanner.plan` groups the orders into a single wave:

```scala
val wavePlan = WavePlanner.plan(
  List(order1, order2), OrderGrouping.Single, at(2)
)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

We covered this step in Chapter 9. The planner creates a `Wave.Released`
and two `TaskRequest` values, one per order line.

### Release with FEFO Allocation

The key difference from Chapter 9 is that we now provide a stock
position repository and specify the FEFO allocation strategy:

```scala
val waveReleaseService = WaveReleaseService(
  waveRepo, taskRepo, cgRepo,
  stockPositionRepository = Some(stockRepo),
  allocationStrategy = AllocationStrategy.Fefo,
  referenceDate = LocalDate.of(2027, 1, 15)
)

val releaseResult = waveReleaseService.release(
  wavePlan, at(2), Some(warehouseAreaId)
)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

FEFO stands for First Expiry, First Out. The `StockAllocationPolicy`
sorts available stock positions by `expirationDate` ascending. The
early-expiry lot (March 2027) comes before the late-expiry lot
(September 2027). Since the early lot has 400 available and we only need
80, all 80 units are allocated from `ASP-2026-A`.

The test verifies this:

```scala
it("allocates stock using FEFO (earliest expiry first)"):
  val allocatedPositionIds =
    releaseResult.stockAllocations
      .flatMap(_.allocations.map(_.stockPositionId)).distinct
  val earlyExpiryPosition = stockRepo.store.values
    .find(_.lotAttributes.expirationDate
      .contains(LocalDate.of(2027, 3, 31))).value
  assert(allocatedPositionIds.contains(earlyExpiryPosition.id))

it("locks allocated quantity on the stock position"):
  val earlyExpiryPosition = stockRepo.store.values
    .find(_.lotAttributes.expirationDate
      .contains(LocalDate.of(2027, 3, 31))).value
  assert(earlyExpiryPosition.allocatedQuantity == 80)
  assert(earlyExpiryPosition.availableQuantity == 400 - 80)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

After allocation, the 4-bucket model for the early-expiry position
reads: 400 on-hand, 320 available, 80 allocated, 0 reserved, 0 blocked.
The `onHand == available + allocated + reserved + blocked` invariant
holds.

The late-expiry lot sits untouched at 150 on-hand, 150 available. FEFO
skipped it entirely because the earlier lot had sufficient quantity.

@:callout(info)

Each created task now carries a `stockPositionId` linking it
to the allocated position. The `WaveReleaseService.allocateAndEnrich`
method sets this after allocation, so the `TaskCompletionService` knows
which stock position to consume from later.

@:@


## 10:00 -- Picking with Shortpick

Two tasks are in the repository: one for 50 units, one for 30 units.
A picker needs to allocate, assign, and complete each one.

### Full Pick: 50 of 50

```scala
it("completes a full pick and consumes stock"):
  val task1 = taskRepo.store.values.collectFirst {
    case t: Task.Planned if t.requestedQuantity == 50 => t
  }.value
  val (allocated, _) = task1.allocate(pickLocationA, bufferZone, at(4))
  taskRepo.store(allocated.id) = allocated
  val (assigned, _) = allocated.assign(picker, at(4))
  taskRepo.store(assigned.id) = assigned

  val result = taskCompletionService.complete(
    assigned.id, 50, true, at(4)
  ).value
  assert(result.completed.actualQuantity == 50)
  assert(result.shortpick.isEmpty)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The task moves through its full typestate chain: `Planned` to
`Allocated` to `Assigned` to `Completed`. The actual quantity equals
the requested quantity, so `ShortpickPolicy` returns `None`. The
`TaskCompletionService` calls `sp.consumeAllocated(50, at)` on the
stock position, moving 50 units from the allocated bucket to consumed
(reducing both on-hand and allocated).

### Shortpick: 25 of 30

The second pick does not go as planned:

```scala
it("handles a shortpick: picker finds only 25 of 30 requested"):
  val task2 = taskRepo.store.values.collectFirst {
    case t: Task.Planned if t.requestedQuantity == 30 => t
  }.value
  val (allocated, _) = task2.allocate(pickLocationA, bufferZone, at(4))
  taskRepo.store(allocated.id) = allocated
  val (assigned, _) = allocated.assign(picker, at(4))
  taskRepo.store(assigned.id) = assigned

  val result = taskCompletionService.complete(
    assigned.id, 25, true, at(4)
  ).value
  assert(result.completed.actualQuantity == 25)
  assert(result.shortpick.isDefined)
  val (replacement, _) = result.shortpick.value
  assert(replacement.requestedQuantity == 5)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The picker scans 25 units but the task requested 30. The
`TaskCompletionService.consumeOrDeallocateStock` method handles this
partial pick in two steps:

1. **Consume 25**: `sp.consumeAllocated(25, at)` reduces on-hand by 25
   and allocated by 25.
2. **Deallocate 5**: `sp.deallocate(5, at)` moves the remaining 5 units
   from allocated back to available.

Then `ShortpickPolicy` sees that `actualQuantity (25) <
requestedQuantity (30)` and creates a replacement `Task.Planned` for
the remainder of 5 units.

### Final Stock State After Picking

```scala
it("consumed allocated stock after picks"):
  val earlyExpiryPosition = stockRepo.store.values
    .find(_.lotAttributes.expirationDate
      .contains(LocalDate.of(2027, 3, 31))).value
  assert(earlyExpiryPosition.allocatedQuantity == 0)
  assert(earlyExpiryPosition.onHandQuantity == 325)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Let's trace the numbers for the early-expiry aspirin:

| Step | On-hand | Available | Allocated |
|------|---------|-----------|-----------|
| After inbound | 400 | 400 | 0 |
| After allocation (80) | 400 | 320 | 80 |
| After full pick (50) | 350 | 320 | 30 |
| After partial pick (25) | 325 | 320 | 5 |
| After deallocation (5) | 325 | 325 | 0 |

On-hand dropped from 400 to 325 (75 units picked). Allocated returned to
0 because the full pick consumed 50, the partial pick consumed 25, and
the remaining 5 were deallocated back to available. The invariant holds
at every step: `325 == 325 + 0 + 0 + 0`.


## 14:00 -- Cycle Counting

After the morning's picking activity, the warehouse supervisor orders a
cycle count of bandages. The bandage stock position started at 500
on-hand, and nobody has touched it yet.

### Creating the Cycle Count

```scala
val cycleCount = CycleCount.New(
  CycleCountId(), warehouseAreaId,
  List(bandageSkuId), CountType.Planned, CountMethod.Blind
)
val (inProgress, startedEvent) = cycleCount.start(at(8))
ccRepo.save(inProgress, startedEvent)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The cycle count is created with `CountMethod.Blind`. In a blind count,
the operator does not see the expected quantity. They count what they
find and report the number. This prevents confirmation bias: the counter
cannot simply agree with the system.

The typestate transition moves from `New` to `InProgress`. Only the
`InProgress` state exposes the `complete` method.

### Creating Count Tasks

```scala
val bandagePosition = stockRepo.store.values
  .find(_.skuId == bandageSkuId).value
val stockSnapshots =
  Map((bandageSkuId, pickLocationB) -> bandagePosition.onHandQuantity)
val countTasks = CountCreationPolicy(inProgress, stockSnapshots, at(8))
countTasks.foreach((ct, e) => ctRepo.save(ct, e))
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`CountCreationPolicy` is a stateless policy object. It takes the
in-progress cycle count, a map of (SKU, location) pairs to expected
quantities, and produces `CountTask.Pending` instances. The policy
filters the snapshot map to only include SKUs listed in the cycle count.

The test verifies that the expected quantity comes from the stock
snapshot:

```scala
it("creates count tasks with expected quantities from stock snapshot"):
  assert(countTasks.size == 1)
  val (pending, _) = countTasks.head
  assert(pending.expectedQuantity == 500)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

### Counting and Recording

The count task walks through its lifecycle:

```scala
val (pending, _) = countTasks.head
val (assigned, assignedEvent) = pending.assign(countOperator, at(8))
ctRepo.save(assigned, assignedEvent)

val (recorded, recordedEvent) = assigned.record(497, at(8))
ctRepo.save(recorded, recordedEvent)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`Pending` to `Assigned` to `Recorded`. The operator counts 497 units.
The system expected 500. The `record` method computes the variance
automatically:

```scala
it("records actual count with variance"):
  assert(recorded.actualQuantity == 497)
  assert(recorded.variance == -3)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

A variance of -3 means 3 units are missing. This is likely shrinkage
(theft, damage, or miscounting during prior operations).

### Completing the Cycle Count

```scala
val completionService = CountCompletionService(ccRepo, ctRepo)
val completionResult =
  completionService.tryComplete(inProgress.id, at(8)).value
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`CountCompletionService.tryComplete` validates three conditions: (1) the
cycle count exists, (2) it is in `InProgress` state, and (3) all count
tasks are terminal (`Recorded` or `Cancelled`). If all conditions pass,
it transitions the cycle count to `Completed` and collects all non-zero
variances:

```scala
it("completes the cycle count and reports variances"):
  assert(completionResult.completed
    .isInstanceOf[CycleCount.Completed])
  assert(completionResult.variances.size == 1)
  assert(completionResult.variances.head.variance == -3)
  assert(completionResult.variances.head.countedBy == countOperator)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The `CountVariance` record carries the `countedBy` field. This will
become critical in the next section.


## 16:00 -- SOX-Compliant Adjustment

The cycle count found a -3 variance. Before we can adjust the stock, we
must satisfy SOX Section 404 segregation of duties: the person who
adjusts inventory must not be the same person who counted it.

### Segregation of Duties Violation

```scala
it("rejects adjustment when counter is the same as adjuster"):
  val variance = CountVariance(
    countTaskId = ctRepo.store.keys.head,
    skuId = bandageSkuId,
    locationId = pickLocationB,
    expectedQuantity = 500,
    actualQuantity = 497,
    variance = -3,
    countedBy = countOperator
  )

  val result = AdjustmentService.adjust(
    variance,
    adjustedBy = countOperator, // same person: SOX violation
    AdjustmentReasonCode.CycleCountAdjustment,
    at(10)
  )
  assert(result.isLeft)
  assert(result.left.value
    .isInstanceOf[AdjustmentError.SegregationOfDutiesViolation])
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`AdjustmentService` is a stateless object (like a policy). Its single
`adjust` method checks one condition:

```scala
if adjustedBy == variance.countedBy then
  Left(AdjustmentError.SegregationOfDutiesViolation(
    variance.countedBy, adjustedBy
  ))
else Right(AdjustmentResult(variance, adjustedBy, reasonCode))
```

<small>*File: core/src/main/scala/neon/core/AdjustmentService.scala*</small>

The `countOperator` cannot adjust their own count. The `Either` return
type makes this a data-driven decision, not an exception. The caller can
pattern match on the error ADT and present a clear message: "The counter
and the adjuster must be different people."

### Supervisor Adjustment

A different user adjusts the variance:

```scala
it("approves adjustment when supervisor (different user) adjusts"):
  val result = AdjustmentService.adjust(
    variance,
    adjustedBy = supervisor,
    AdjustmentReasonCode.Shrinkage,
    at(10)
  )
  assert(result.isRight)
  val adjustmentResult = result.value
  assert(adjustmentResult.adjustedBy == supervisor)
  assert(adjustmentResult.reasonCode == AdjustmentReasonCode.Shrinkage)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The `supervisor` is a different `UserId` than `countOperator`, so the
check passes. The reason code `Shrinkage` will be recorded on the stock
event for audit purposes.

### Applying the Stock Adjustment

```scala
it("applies the stock adjustment"):
  val bandagePosition = stockRepo.store.values
    .find(_.skuId == bandageSkuId).value
  val (adjusted, adjustEvent) =
    bandagePosition.adjust(-3, AdjustmentReasonCode.Shrinkage, at(10))
  stockRepo.save(adjusted, adjustEvent)
  assert(adjusted.onHandQuantity == 497)
  assert(adjusted.availableQuantity == 497)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`StockPosition.adjust` takes a delta (positive for surplus, negative for
shrinkage) and modifies both `onHandQuantity` and `availableQuantity`.
The invariant still holds: `497 == 497 + 0 + 0 + 0`. The
`StockPositionEvent.Adjusted` event records the delta, the reason code,
and the timestamp for the complete audit trail.

@:callout(info)

The adjustment flow is deliberately two-step: first
`AdjustmentService.adjust` validates and produces an `AdjustmentResult`,
then the caller applies the result to the stock position. This
separation means the validation logic (SOX compliance) lives in the
`core` module while the stock mutation stays on the `StockPosition`
aggregate where it belongs.

@:@


## 17:00 -- Workstation Operations

The day is winding down. The final section exercises the workstation
aggregate's typestate machine.

### Enabling a Workstation

```scala
it("starts in Picking mode when enabled"):
  val disabled = Workstation.Disabled(
    WorkstationId(), WorkstationType.PutWall, 8
  )
  val (idle, enabledEvent) = disabled.enable(at(11))
  assert(idle.mode == WorkstationMode.Picking)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

A `PutWall` workstation with 8 slots starts in `Disabled` state. The
`enable` method transitions it to `Idle` with a default mode of
`WorkstationMode.Picking`. The mode is set by the `enable` method, not
by the caller. Every workstation starts its day in Picking mode.

### Mode Switching

An `Idle` workstation can switch between modes:

```scala
it("switches from Picking to Receiving mode"):
  val disabled = Workstation.Disabled(
    WorkstationId(), WorkstationType.PutWall, 8
  )
  val (idle, _) = disabled.enable(at(11))
  val (receivingMode, switchEvent) =
    idle.switchMode(WorkstationMode.Receiving, at(11))
  assert(receivingMode.mode == WorkstationMode.Receiving)
  assert(switchEvent.previousMode == WorkstationMode.Picking)
  assert(switchEvent.newMode == WorkstationMode.Receiving)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

`switchMode` returns the new `Idle` state and a `ModeSwitched` event
that captures both the previous and new modes. The workstation remains
in the `Idle` typestate; only the `mode` field changes.

The workstation can also switch to `Counting` mode for cycle count
operations:

```scala
it("switches to Counting mode for cycle count"):
  val disabled = Workstation.Disabled(
    WorkstationId(), WorkstationType.PutWall, 8
  )
  val (idle, _) = disabled.enable(at(11))
  val (countingMode, _) =
    idle.switchMode(WorkstationMode.Counting, at(11))
  assert(countingMode.mode == WorkstationMode.Counting)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

### Compile-Time Enforcement on Active Workstations

Here is where the typestate pattern shines:

```scala
it("cannot switch mode while Active (processing work)"):
  val disabled = Workstation.Disabled(
    WorkstationId(), WorkstationType.PutWall, 8
  )
  val (idle, _) = disabled.enable(at(11))
  val (active, _) =
    idle.assign(java.util.UUID.randomUUID(), at(11))
  // Active state does not expose switchMode:
  // compile-time enforcement
  assert(active.isInstanceOf[Workstation.Active])
  val (backToIdle, _) = active.release(at(11))
  val (relocated, _) =
    backToIdle.switchMode(WorkstationMode.Relocation, at(11))
  assert(relocated.mode == WorkstationMode.Relocation)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

The `Active` case class does not have a `switchMode` method. It is not a
runtime check that throws an exception. It is not an `Either` that
returns an error. The method simply does not exist on `Active`. If you
tried to write `active.switchMode(...)`, the compiler would reject it.

To switch modes, you must first `release` the active assignment (which
returns to `Idle`), then call `switchMode`. The type system encodes the
business rule directly.

@:callout(info)

The `assign` method on `Idle` takes a `UUID` assignment
identifier and transitions to `Active`. The `release` method on
`Active` returns to `Idle`, preserving the current mode. This
`Idle` to `Active` to `Idle` cycle mirrors the physical workflow: a
workstation receives a consolidation group, processes it, and then
becomes available for the next one.

@:@


## End of Day Summary

The final `describe` block verifies the cumulative state across all
operations:

```scala
it("all stock positions reflect the day's operations"):
  val positions = stockRepo.store.values.toList
  assert(positions.size == 3)

  val aspirinEarly = positions
    .find(_.lotAttributes.expirationDate
      .contains(LocalDate.of(2027, 3, 31))).value
  assert(aspirinEarly.onHandQuantity == 325)
  assert(aspirinEarly.allocatedQuantity == 0)

  val aspirinLate = positions
    .find(_.lotAttributes.expirationDate
      .contains(LocalDate.of(2027, 9, 30))).value
  assert(aspirinLate.onHandQuantity == 150)
  assert(aspirinLate.allocatedQuantity == 0)

  val bandages = positions
    .find(_.skuId == bandageSkuId).value
  assert(bandages.onHandQuantity == 497)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Three stock positions, three stories:

- **Aspirin early-expiry (ASP-2026-A)**: Started at 200, received 200
  more (total 400), FEFO allocated 80, picked 75 (50 full + 25 partial),
  deallocated remainder 5. Final: 325 on-hand, 0 allocated.
- **Aspirin late-expiry (ASP-2026-B)**: Started at 150, untouched.
  FEFO never reached it because the early lot had sufficient stock.
  Final: 150 on-hand, 0 allocated.
- **Bandages (BND-2026)**: Started at 500, blind cycle count found 497,
  supervisor approved shrinkage adjustment. Final: 497 on-hand.

### The Audit Trail

```scala
it("events trace the complete audit trail"):
  assert(stockRepo.events.nonEmpty)
  val createdEvents = stockRepo.events.collect {
    case e: StockPositionEvent.Created => e
  }
  assert(createdEvents.size == 3)

  val allocatedEvents = stockRepo.events.collect {
    case e: StockPositionEvent.Allocated => e
  }
  assert(allocatedEvents.nonEmpty)

  val consumedEvents = stockRepo.events.collect {
    case e: StockPositionEvent.AllocatedConsumed => e
  }
  assert(consumedEvents.nonEmpty)

  val adjustedEvents = stockRepo.events.collect {
    case e: StockPositionEvent.Adjusted => e
  }
  assert(adjustedEvents.size == 1)
  assert(adjustedEvents.head.reasonCode
    == AdjustmentReasonCode.Shrinkage)
```

<small>*File: core/src/test/scala/neon/core/WarehouseDaySimulationSuite.scala*</small>

Every mutation throughout the day produced an immutable event. The
`stockRepo.events` list buffer contains `Created`, `QuantityAdded`,
`Allocated`, `AllocatedConsumed`, `Deallocated`, and `Adjusted` events
in chronological order. In production, these events feed CQRS
projections (Chapter 12), audit logs, and downstream analytics. Here in
the test, they let us verify that every transition happened and that
nothing was silently lost.


## What We Learned

This single test file exercises every domain module in the system:

| Time | Module | Aggregates |
|------|--------|------------|
| 06:00 | `inbound-delivery`, `goods-receipt`, `stock-position`, `task` | InboundDelivery, GoodsReceipt, StockPosition, Task |
| 08:00 | `wave`, `task`, `stock-position`, `consolidation-group` | Wave, Task, StockPosition, ConsolidationGroup |
| 10:00 | `task`, `stock-position` | Task, StockPosition |
| 14:00 | `cycle-count`, `count-task`, `stock-position` | CycleCount, CountTask, StockPosition |
| 16:00 | `stock-position` | StockPosition |
| 17:00 | `workstation` | Workstation |

Every pattern from Parts I through III contributed:

**Typestate encoding** prevented invalid transitions at compile time.
We could not complete a `New` delivery, confirm an empty receipt,
adjust stock with the same user who counted it (via `Either`), or
switch modes on an `Active` workstation.

**The 4-bucket stock model** tracked inventory through five distinct
operations: creation, addition, allocation, consumption, deallocation,
and adjustment. The `onHand == available + allocated + reserved +
blocked` invariant held at every step, enforced by `require` in the
`validateInvariant` method.

**Stateless policies** made pure decisions. `CountCreationPolicy`
produced count tasks from stock snapshots. `StockAllocationPolicy`
sorted positions by expiration date. `ShortpickPolicy` computed a
replacement quantity. None of them touched a repository.

**Services** orchestrated cascades across aggregates.
`InboundReceivingService` created tasks and updated stock.
`WaveReleaseService` allocated stock and enriched tasks.
`TaskCompletionService` consumed stock, checked for shortpicks, and
managed wave completion. `CountCompletionService` collected variances.
`AdjustmentService` enforced SOX rules.

**In-memory repositories** made the entire simulation possible without
any infrastructure. Seven mutable maps and seven event buffers replaced
Pekko actors, Cluster Sharding, PostgreSQL, and R2DBC projections. The
domain code does not know the difference. This is the hexagonal
architecture payoff from Chapter 8: the same domain logic that runs in
this 619-line test file runs in the production distributed system.

The test is living documentation. If you want to understand the entire
system in one sitting, read `WarehouseDaySimulationSuite` top to bottom. When a domain rule changes, the test
breaks in a specific, traceable location. When someone asks "how does
FEFO allocation work?" or "what happens on a shortpick?" or "who can
approve an inventory adjustment?", the answer is in the test, verified
on every CI run.

We have now seen the domain model in action across a complete warehouse
day. In the next chapters, we will turn to the cross-cutting concerns
that production systems demand: serialization, error handling,
configuration, and deployment.
