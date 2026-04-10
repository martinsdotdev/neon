package neon.core

import neon.common.{
  AdjustmentReasonCode,
  AllocationStrategy,
  ContainerId,
  ConsolidationGroupId,
  CountMethod,
  CountTaskId,
  CountType,
  CycleCountId,
  GoodsReceiptId,
  HandlingUnitId,
  HandlingUnitStockId,
  InboundDeliveryId,
  InventoryStatus,
  LocationId,
  Lot,
  LotAttributes,
  OrderId,
  PackagingLevel,
  Priority,
  SkuId,
  SlotCode,
  StockLockType,
  StockPositionId,
  TaskId,
  UserId,
  WarehouseAreaId,
  WaveId,
  WorkstationId,
  WorkstationMode
}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.counttask.{CountTask, CountTaskEvent, CountTaskRepository}
import neon.cyclecount.{CycleCount, CycleCountEvent, CycleCountRepository}
import neon.goodsreceipt.{GoodsReceipt, GoodsReceiptEvent, ReceivedLine}
import neon.handlingunitstock.HandlingUnitStock
import neon.inbounddelivery.InboundDelivery
import neon.order.{Order, OrderLine}
import neon.stockposition.{StockPosition, StockPositionEvent, StockPositionRepository}
import neon.task.{Task, TaskEvent, TaskRepository, TaskType}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{OrderGrouping, Wave, WaveEvent, WavePlanner, WaveRepository}
import neon.workstation.{Workstation, WorkstationType}
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.{Instant, LocalDate}
import scala.collection.mutable

/** Simulates a full day of warehouse operations exercising all new domain modules end-to-end:
  *
  *   - 06:00 Inbound: receive a shipment of 3 SKUs
  *   - 08:00 Outbound: plan and release a wave with FEFO allocation
  *   - 10:00 Picking: complete tasks (including a shortpick)
  *   - 14:00 Cycle count: blind count with variance detection
  *   - 16:00 Adjustment: SOX-compliant variance resolution
  *   - 17:00 Workstation mode switching
  */
class WarehouseDaySimulationSuite extends AnyFunSpec with OptionValues with EitherValues:

  // --- Warehouse topology ---
  val warehouseAreaId = WarehouseAreaId()
  val pickLocationA = LocationId()
  val pickLocationB = LocationId()
  val pickLocationC = LocationId()
  val dockDoor = LocationId()
  val bufferZone = LocationId()

  // --- SKUs ---
  val aspirinSkuId = SkuId() // pharma: requires FEFO
  val bandageSkuId = SkuId() // medical supply
  val syringeSkuId = SkuId() // medical device

  // --- Lot attributes (GS1 AI-10/17) ---
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
  val bandageLot = LotAttributes(
    lot = Some(Lot("BND-2026")),
    productionDate = Some(LocalDate.of(2026, 2, 1))
  )
  val syringeLot = LotAttributes(
    lot = Some(Lot("SYR-2026")),
    expirationDate = Some(LocalDate.of(2028, 12, 31)),
    productionDate = Some(LocalDate.of(2026, 3, 1))
  )

  // --- Users ---
  val receivingOperator = UserId()
  val picker = UserId()
  val countOperator = UserId()
  val supervisor = UserId() // different from countOperator for SOX

  // --- Containers ---
  val inboundPallet = ContainerId()
  val pickTote = ContainerId()

  // --- Time progression ---
  val dayStart = Instant.parse("2027-01-15T06:00:00Z")
  def at(hoursAfterStart: Int): Instant = dayStart.plusSeconds(hoursAfterStart * 3600L)

  // --- In-memory repositories ---
  class InMemoryStockPositionRepository extends StockPositionRepository:
    val store: mutable.Map[StockPositionId, StockPosition] = mutable.Map.empty
    val events: mutable.ListBuffer[StockPositionEvent] = mutable.ListBuffer.empty
    def findById(id: StockPositionId): Option[StockPosition] = store.get(id)
    def findBySkuAndArea(skuId: SkuId, warehouseAreaId: WarehouseAreaId): List[StockPosition] =
      store.values.filter(sp => sp.skuId == skuId && sp.warehouseAreaId == warehouseAreaId).toList
    def save(stockPosition: StockPosition, event: StockPositionEvent): Unit =
      store(stockPosition.id) = stockPosition
      events += event

  class InMemoryTaskRepository extends TaskRepository:
    val store: mutable.Map[TaskId, Task] = mutable.Map.empty
    val events: mutable.ListBuffer[TaskEvent] = mutable.ListBuffer.empty
    def findById(id: TaskId): Option[Task] = store.get(id)
    def findByWaveId(waveId: WaveId): List[Task] =
      store.values.filter(_.waveId.contains(waveId)).toList
    def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task] =
      store.values.filter(_.handlingUnitId.contains(handlingUnitId)).toList
    def save(task: Task, event: TaskEvent): Unit =
      store(task.id) = task
      events += event
    def saveAll(entries: List[(Task, TaskEvent)]): Unit =
      entries.foreach((t, e) => save(t, e))

  class InMemoryWaveRepository extends WaveRepository:
    val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
    val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
    def findById(id: WaveId): Option[Wave] = store.get(id)
    def save(wave: Wave, event: WaveEvent): Unit =
      store(wave.id) = wave
      events += event

  class InMemoryConsolidationGroupRepository extends ConsolidationGroupRepository:
    val store: mutable.Map[ConsolidationGroupId, ConsolidationGroup] = mutable.Map.empty
    val events: mutable.ListBuffer[ConsolidationGroupEvent] = mutable.ListBuffer.empty
    def findById(id: ConsolidationGroupId): Option[ConsolidationGroup] = store.get(id)
    def findByWaveId(waveId: WaveId): List[ConsolidationGroup] =
      store.values.filter(_.waveId == waveId).toList
    def save(cg: ConsolidationGroup, event: ConsolidationGroupEvent): Unit =
      store(cg.id) = cg
      events += event
    def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit =
      entries.foreach((cg, e) => save(cg, e))

  class InMemoryTransportOrderRepository extends TransportOrderRepository:
    val store: mutable.Map[neon.common.TransportOrderId, TransportOrder] = mutable.Map.empty
    val events: mutable.ListBuffer[TransportOrderEvent] = mutable.ListBuffer.empty
    def findById(id: neon.common.TransportOrderId): Option[TransportOrder] = store.get(id)
    def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[TransportOrder] =
      store.values.filter(_.handlingUnitId == handlingUnitId).toList
    def save(to: TransportOrder, event: TransportOrderEvent): Unit =
      store(to.id) = to
      events += event
    def saveAll(entries: List[(TransportOrder, TransportOrderEvent)]): Unit =
      entries.foreach((to, e) => save(to, e))

  class InMemoryCycleCountRepository extends CycleCountRepository:
    val store: mutable.Map[CycleCountId, CycleCount] = mutable.Map.empty
    val events: mutable.ListBuffer[CycleCountEvent] = mutable.ListBuffer.empty
    def findById(id: CycleCountId): Option[CycleCount] = store.get(id)
    def save(cc: CycleCount, event: CycleCountEvent): Unit =
      store(cc.id) = cc
      events += event

  class InMemoryCountTaskRepository extends CountTaskRepository:
    val store: mutable.Map[CountTaskId, CountTask] = mutable.Map.empty
    val events: mutable.ListBuffer[CountTaskEvent] = mutable.ListBuffer.empty
    def findById(id: CountTaskId): Option[CountTask] = store.get(id)
    def findByCycleCountId(cycleCountId: CycleCountId): List[CountTask] =
      store.values.filter(_.cycleCountId == cycleCountId).toList
    def save(ct: CountTask, event: CountTaskEvent): Unit =
      store(ct.id) = ct
      events += event
    def saveAll(entries: List[(CountTask, CountTaskEvent)]): Unit =
      entries.foreach((ct, e) => save(ct, e))

  describe("A full day of warehouse operations"):

    // Shared state across the day
    val stockRepo = InMemoryStockPositionRepository()
    val taskRepo = InMemoryTaskRepository()
    val waveRepo = InMemoryWaveRepository()
    val cgRepo = InMemoryConsolidationGroupRepository()
    val toRepo = InMemoryTransportOrderRepository()
    val ccRepo = InMemoryCycleCountRepository()
    val ctRepo = InMemoryCountTaskRepository()

    // ====================================================================
    // 06:00 - INBOUND: Receive a shipment of aspirin (2 lots), bandages
    // ====================================================================

    describe("06:00 inbound receiving"):

      // Create stock positions for the received goods
      val (aspirinEarly, aspirinEarlyEvent) =
        StockPosition.create(aspirinSkuId, warehouseAreaId, aspirinLotEarlyExpiry, 200, at(0))
      stockRepo.save(aspirinEarly, aspirinEarlyEvent)

      val (aspirinLate, aspirinLateEvent) =
        StockPosition.create(aspirinSkuId, warehouseAreaId, aspirinLotLateExpiry, 150, at(0))
      stockRepo.save(aspirinLate, aspirinLateEvent)

      val (bandageStock, bandageEvent) =
        StockPosition.create(bandageSkuId, warehouseAreaId, bandageLot, 500, at(0))
      stockRepo.save(bandageStock, bandageEvent)

      // Create an inbound delivery
      val delivery = InboundDelivery.New(
        InboundDeliveryId(),
        aspirinSkuId,
        PackagingLevel.Each,
        aspirinLotEarlyExpiry,
        200
      )

      it("creates inbound delivery in New state"):
        assert(delivery.isInstanceOf[InboundDelivery.New])
        assert(delivery.expectedQuantity == 200)

      // Start receiving
      val (receiving, receivingEvent) = delivery.startReceiving(at(0))

      it("transitions to Receiving state"):
        assert(receiving.isInstanceOf[InboundDelivery.Receiving])

      // Record received quantities
      val (afterReceive, receiveEvent) = receiving.receive(200, 0, at(0))

      it("records received quantity"):
        assert(afterReceive.receivedQuantity == 200)
        assert(afterReceive.isFullyReceived)

      // Complete receiving
      val (received, receivedEvent) = afterReceive.complete(at(0))

      it("completes the delivery"):
        assert(received.isInstanceOf[InboundDelivery.Received])

      // Create a goods receipt
      val receipt = GoodsReceipt.Open(GoodsReceiptId(), delivery.id, Nil)

      it("creates goods receipt in Open state"):
        assert(receipt.isInstanceOf[GoodsReceipt.Open])

      // Record a line
      val receivedLine = ReceivedLine(
        aspirinSkuId,
        200,
        PackagingLevel.Each,
        aspirinLotEarlyExpiry,
        Some(inboundPallet)
      )
      val (withLine, lineEvent) = receipt.recordLine(receivedLine, at(0))

      it("records a received line"):
        assert(withLine.receivedLines.size == 1)

      // Confirm the receipt
      val (confirmed, confirmedEvent) = withLine.confirm(at(0))

      it("confirms the goods receipt"):
        assert(confirmed.isInstanceOf[GoodsReceipt.Confirmed])

      // Process through InboundReceivingService
      val inboundService = InboundReceivingService(taskRepo, stockRepo)
      val inboundResult = inboundService.processConfirmedReceipt(
        confirmed,
        OrderId(),
        at(0),
        Some(aspirinEarly.id)
      )

      it("creates putaway tasks from the receipt"):
        assert(inboundResult.tasks.nonEmpty)
        val (putawayTask, _) = inboundResult.tasks.head
        assert(putawayTask.taskType == TaskType.Putaway)

      it("updates stock position with received quantity"):
        // Verify the QuantityAdded event was recorded for the inbound receipt
        val addedEvents = stockRepo.events.collect { case e: StockPositionEvent.QuantityAdded => e }
        assert(addedEvents.exists(_.quantity == 200))

    // ====================================================================
    // 08:00 - OUTBOUND: Plan and release a wave with FEFO allocation
    // ====================================================================

    describe("08:00 outbound wave with FEFO allocation"):

      val order1 = Order(
        OrderId(),
        Priority.High,
        List(OrderLine(aspirinSkuId, PackagingLevel.Each, 50))
      )
      val order2 = Order(
        OrderId(),
        Priority.Normal,
        List(OrderLine(aspirinSkuId, PackagingLevel.Each, 30))
      )

      val wavePlan = WavePlanner.plan(List(order1, order2), OrderGrouping.Single, at(2))

      val waveReleaseService = WaveReleaseService(
        waveRepo,
        taskRepo,
        cgRepo,
        stockPositionRepository = Some(stockRepo),
        allocationStrategy = AllocationStrategy.Fefo,
        referenceDate = LocalDate.of(2027, 1, 15)
      )

      val releaseResult = waveReleaseService.release(wavePlan, at(2), Some(warehouseAreaId))

      it("creates tasks for both orders"):
        assert(releaseResult.tasks.size == 2)

      it("allocates stock using FEFO (earliest expiry first)"):
        assert(releaseResult.stockAllocations.nonEmpty)
        // Both tasks should draw from the early-expiry lot (March 2027)
        // because FEFO sorts by expiration date ascending
        val allocatedPositionIds =
          releaseResult.stockAllocations.flatMap(_.allocations.map(_.stockPositionId)).distinct
        val earlyExpiryPosition = stockRepo.store.values
          .find(_.lotAttributes.expirationDate.contains(LocalDate.of(2027, 3, 31)))
          .value
        assert(allocatedPositionIds.contains(earlyExpiryPosition.id))

      it("locks allocated quantity on the stock position"):
        val earlyExpiryPosition = stockRepo.store.values
          .find(_.lotAttributes.expirationDate.contains(LocalDate.of(2027, 3, 31)))
          .value
        // 50 + 30 = 80 units allocated from the early-expiry position
        assert(earlyExpiryPosition.allocatedQuantity == 80)
        assert(
          earlyExpiryPosition.availableQuantity == 400 - 80
        ) // 400 from inbound + 200 initial - 80 allocated

      it("sets stockPositionId on created tasks"):
        releaseResult.tasks.foreach { (planned, event) =>
          assert(planned.stockPositionId.isDefined)
        }

    // ====================================================================
    // 10:00 - PICKING: Complete tasks (including a shortpick)
    // ====================================================================

    describe("10:00 picking with shortpick"):

      val taskCompletionService = TaskCompletionService(
        taskRepo,
        waveRepo,
        cgRepo,
        toRepo,
        VerificationProfile.disabled,
        stockPositionRepository = Some(stockRepo)
      )

      // Allocate and assign the first task
      it("completes a full pick and consumes stock"):
        val task1 = taskRepo.store.values.collectFirst {
          case t: Task.Planned if t.requestedQuantity == 50 => t
        }.value
        val (allocated, _) = task1.allocate(pickLocationA, bufferZone, at(4))
        taskRepo.store(allocated.id) = allocated
        val (assigned, _) = allocated.assign(picker, at(4))
        taskRepo.store(assigned.id) = assigned

        val result = taskCompletionService.complete(assigned.id, 50, true, at(4)).value
        assert(result.completed.actualQuantity == 50)
        assert(result.shortpick.isEmpty)

      it("handles a shortpick: picker finds only 25 of 30 requested"):
        val task2 = taskRepo.store.values.collectFirst {
          case t: Task.Planned if t.requestedQuantity == 30 => t
        }.value
        val (allocated, _) = task2.allocate(pickLocationA, bufferZone, at(4))
        taskRepo.store(allocated.id) = allocated
        val (assigned, _) = allocated.assign(picker, at(4))
        taskRepo.store(assigned.id) = assigned

        val result = taskCompletionService.complete(assigned.id, 25, true, at(4)).value
        assert(result.completed.actualQuantity == 25)
        assert(result.shortpick.isDefined)
        val (replacement, _) = result.shortpick.value
        assert(replacement.requestedQuantity == 5) // remainder

      it("consumed allocated stock after picks"):
        val earlyExpiryPosition = stockRepo.store.values
          .find(_.lotAttributes.expirationDate.contains(LocalDate.of(2027, 3, 31)))
          .value
        // Started at 80 allocated. Full pick consumed 50, partial pick consumed 25.
        // TaskCompletionService deallocates the remainder (5) on shortpick.
        // 80 - 50 - 25 - 5 = 0 allocated
        assert(earlyExpiryPosition.allocatedQuantity == 0)
        // On-hand: 400 - 50 - 25 = 325
        assert(earlyExpiryPosition.onHandQuantity == 325)

    // ====================================================================
    // 14:00 - CYCLE COUNT: Blind count with variance detection
    // ====================================================================

    describe("14:00 cycle count (blind)"):

      // Create a cycle count for bandages
      val cycleCount = CycleCount.New(
        CycleCountId(),
        warehouseAreaId,
        List(bandageSkuId),
        CountType.Planned,
        CountMethod.Blind
      )

      // Start the cycle count
      val (inProgress, startedEvent) = cycleCount.start(at(8))
      ccRepo.save(inProgress, startedEvent)

      it("creates a cycle count in InProgress state"):
        assert(inProgress.isInstanceOf[CycleCount.InProgress])
        assert(inProgress.countMethod == CountMethod.Blind)

      // Create count tasks from stock snapshots
      val bandagePosition = stockRepo.store.values
        .find(_.skuId == bandageSkuId)
        .value
      val stockSnapshots = Map((bandageSkuId, pickLocationB) -> bandagePosition.onHandQuantity)
      val countTasks = CountCreationPolicy(inProgress, stockSnapshots, at(8))
      countTasks.foreach((ct, e) => ctRepo.save(ct, e))

      it("creates count tasks with expected quantities from stock snapshot"):
        assert(countTasks.size == 1)
        val (pending, _) = countTasks.head
        assert(pending.expectedQuantity == 500)

      // Assign the count task to an operator
      val (pending, _) = countTasks.head
      val (assigned, assignedEvent) = pending.assign(countOperator, at(8))
      ctRepo.save(assigned, assignedEvent)

      // Operator counts 497 (3 missing: shrinkage)
      val (recorded, recordedEvent) = assigned.record(497, at(8))
      ctRepo.save(recorded, recordedEvent)

      it("records actual count with variance"):
        assert(recorded.actualQuantity == 497)
        assert(recorded.variance == -3)

      // Complete the cycle count
      val completionService = CountCompletionService(ccRepo, ctRepo)
      val completionResult = completionService.tryComplete(inProgress.id, at(8)).value

      it("completes the cycle count and reports variances"):
        assert(completionResult.completed.isInstanceOf[CycleCount.Completed])
        assert(completionResult.variances.size == 1)
        assert(completionResult.variances.head.variance == -3)
        assert(completionResult.variances.head.countedBy == countOperator)

    // ====================================================================
    // 16:00 - ADJUSTMENT: SOX-compliant variance resolution
    // ====================================================================

    describe("16:00 SOX-compliant adjustment"):

      it("rejects adjustment when counter is the same as adjuster"):
        val completionService = CountCompletionService(ccRepo, ctRepo)
        val cycleCountId = ccRepo.store.keys.head
        val completionResult = completionService.tryComplete(cycleCountId, at(10))
        // The cycle count is already completed, so tryComplete returns an error.
        // Instead, use the variance from the earlier step.
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
        assert(result.left.value.isInstanceOf[AdjustmentError.SegregationOfDutiesViolation])

      it("approves adjustment when supervisor (different user) adjusts"):
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
          adjustedBy = supervisor, // different person: SOX compliant
          AdjustmentReasonCode.Shrinkage,
          at(10)
        )
        assert(result.isRight)
        val adjustmentResult = result.value
        assert(adjustmentResult.adjustedBy == supervisor)
        assert(adjustmentResult.reasonCode == AdjustmentReasonCode.Shrinkage)

      it("applies the stock adjustment"):
        val bandagePosition = stockRepo.store.values
          .find(_.skuId == bandageSkuId)
          .value
        val (adjusted, adjustEvent) =
          bandagePosition.adjust(-3, AdjustmentReasonCode.Shrinkage, at(10))
        stockRepo.save(adjusted, adjustEvent)
        assert(adjusted.onHandQuantity == 497)
        assert(adjusted.availableQuantity == 497)

    // ====================================================================
    // 17:00 - WORKSTATION: Mode switching through the day
    // ====================================================================

    describe("17:00 workstation mode switching"):

      it("starts in Picking mode when enabled"):
        val disabled = Workstation.Disabled(WorkstationId(), WorkstationType.PutWall, 8)
        val (idle, enabledEvent) = disabled.enable(at(11))
        assert(idle.mode == WorkstationMode.Picking)

      it("switches from Picking to Receiving mode"):
        val disabled = Workstation.Disabled(WorkstationId(), WorkstationType.PutWall, 8)
        val (idle, _) = disabled.enable(at(11))
        val (receivingMode, switchEvent) = idle.switchMode(WorkstationMode.Receiving, at(11))
        assert(receivingMode.mode == WorkstationMode.Receiving)
        assert(switchEvent.previousMode == WorkstationMode.Picking)
        assert(switchEvent.newMode == WorkstationMode.Receiving)

      it("switches to Counting mode for cycle count"):
        val disabled = Workstation.Disabled(WorkstationId(), WorkstationType.PutWall, 8)
        val (idle, _) = disabled.enable(at(11))
        val (countingMode, _) = idle.switchMode(WorkstationMode.Counting, at(11))
        assert(countingMode.mode == WorkstationMode.Counting)

      it("cannot switch mode while Active (processing work)"):
        val disabled = Workstation.Disabled(WorkstationId(), WorkstationType.PutWall, 8)
        val (idle, _) = disabled.enable(at(11))
        val (active, _) = idle.assign(java.util.UUID.randomUUID(), at(11))
        // Active state does not expose switchMode: compile-time enforcement
        assert(active.isInstanceOf[Workstation.Active])
        // Verify it can be released and then switched
        val (backToIdle, _) = active.release(at(11))
        val (relocated, _) = backToIdle.switchMode(WorkstationMode.Relocation, at(11))
        assert(relocated.mode == WorkstationMode.Relocation)

    // ====================================================================
    // End of day summary
    // ====================================================================

    describe("end of day summary"):

      it("all stock positions reflect the day's operations"):
        val positions = stockRepo.store.values.toList
        assert(positions.size == 3) // aspirin early, aspirin late, bandages

        // Aspirin early: started 200, received 200 more = 400, allocated 80,
        // consumed 75 (50 full + 25 partial), deallocated remainder 5 = 0 allocated
        val aspirinEarly = positions
          .find(_.lotAttributes.expirationDate.contains(LocalDate.of(2027, 3, 31)))
          .value
        assert(aspirinEarly.onHandQuantity == 325) // 400 - 75
        assert(aspirinEarly.allocatedQuantity == 0) // fully consumed + deallocated

        // Aspirin late: untouched at 150 (FEFO skipped it)
        val aspirinLate = positions
          .find(_.lotAttributes.expirationDate.contains(LocalDate.of(2027, 9, 30)))
          .value
        assert(aspirinLate.onHandQuantity == 150)
        assert(aspirinLate.allocatedQuantity == 0)

        // Bandages: started 500, adjusted -3 from cycle count = 497
        val bandages = positions.find(_.skuId == bandageSkuId).value
        assert(bandages.onHandQuantity == 497)

      it("events trace the complete audit trail"):
        // Every mutation was captured as an immutable event
        assert(stockRepo.events.nonEmpty)
        // Created events for initial stock
        val createdEvents = stockRepo.events.collect { case e: StockPositionEvent.Created => e }
        assert(createdEvents.size == 3)
        // Allocation events from wave release
        val allocatedEvents =
          stockRepo.events.collect { case e: StockPositionEvent.Allocated => e }
        assert(allocatedEvents.nonEmpty)
        // Consumption events from picking
        val consumedEvents =
          stockRepo.events.collect { case e: StockPositionEvent.AllocatedConsumed => e }
        assert(consumedEvents.nonEmpty)
        // Adjustment event from cycle count
        val adjustedEvents =
          stockRepo.events.collect { case e: StockPositionEvent.Adjusted => e }
        assert(adjustedEvents.size == 1)
        assert(adjustedEvents.head.reasonCode == AdjustmentReasonCode.Shrinkage)
