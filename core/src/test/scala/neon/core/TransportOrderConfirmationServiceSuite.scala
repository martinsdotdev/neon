package neon.core

import neon.common.{
  ConsolidationGroupId,
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  TransportOrderId,
  UserId,
  WaveId
}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent, HandlingUnitRepository}
import neon.task.{Task, TaskEvent, TaskRepository, TaskType}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

import java.time.Instant
import scala.collection.mutable

class TransportOrderConfirmationServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val bufferLocationId = LocationId()
  val transportOrderId = TransportOrderId()
  val at = Instant.now()

  def pendingTransportOrder(
      id: TransportOrderId = transportOrderId,
      handlingUnitId: HandlingUnitId = handlingUnitId,
      destination: LocationId = bufferLocationId
  ): TransportOrder.Pending =
    TransportOrder.Pending(id, handlingUnitId, destination)

  def confirmedTransportOrder(
      id: TransportOrderId = transportOrderId,
      handlingUnitId: HandlingUnitId = handlingUnitId
  ): TransportOrder.Confirmed =
    TransportOrder.Confirmed(id, handlingUnitId, bufferLocationId)

  def cancelledTransportOrder(
      id: TransportOrderId = transportOrderId,
      handlingUnitId: HandlingUnitId = handlingUnitId
  ): TransportOrder.Cancelled =
    TransportOrder.Cancelled(id, handlingUnitId, bufferLocationId)

  def pickCreatedHandlingUnit(
      id: HandlingUnitId = handlingUnitId,
      currentLocation: LocationId = sourceLocationId
  ): HandlingUnit.PickCreated =
    HandlingUnit.PickCreated(id, PackagingLevel.Each, currentLocation)

  def inBufferHandlingUnit(id: HandlingUnitId = handlingUnitId): HandlingUnit.InBuffer =
    HandlingUnit.InBuffer(id, PackagingLevel.Each, bufferLocationId)

  def emptyHandlingUnit(id: HandlingUnitId = handlingUnitId): HandlingUnit.Empty =
    HandlingUnit.Empty(id, PackagingLevel.Each)

  def completedTask(
      handlingUnitId: HandlingUnitId = handlingUnitId,
      waveId: Option[WaveId] = Some(waveId),
      orderId: OrderId = orderId
  ): Task.Completed =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      10,
      orderId,
      waveId,
      None,
      Some(handlingUnitId),
      sourceLocationId,
      destinationLocationId,
      userId
    )

  def pickedConsolidationGroup(
      waveId: WaveId = waveId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(ConsolidationGroupId(), waveId, orderIds)

  class InMemoryTransportOrderRepository extends TransportOrderRepository:
    val store: mutable.Map[TransportOrderId, TransportOrder] = mutable.Map.empty
    val events: mutable.ListBuffer[TransportOrderEvent] = mutable.ListBuffer.empty
    def findById(id: TransportOrderId): Option[TransportOrder] = store.get(id)
    def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[TransportOrder] =
      store.values.filter(_.handlingUnitId == handlingUnitId).toList
    def save(transportOrder: TransportOrder, event: TransportOrderEvent): Unit =
      store(transportOrder.id) = transportOrder
      events += event
    def saveAll(entries: List[(TransportOrder, TransportOrderEvent)]): Unit =
      entries.foreach { (transportOrder, event) => save(transportOrder, event) }

  class InMemoryHandlingUnitRepository extends HandlingUnitRepository:
    val store: mutable.Map[HandlingUnitId, HandlingUnit] = mutable.Map.empty
    val events: mutable.ListBuffer[HandlingUnitEvent] = mutable.ListBuffer.empty
    def findById(id: HandlingUnitId): Option[HandlingUnit] = store.get(id)
    def findByIds(ids: List[HandlingUnitId]): List[HandlingUnit] =
      ids.flatMap(store.get)
    def save(handlingUnit: HandlingUnit, event: HandlingUnitEvent): Unit =
      store(handlingUnit.id) = handlingUnit
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
      entries.foreach { (task, event) => save(task, event) }

  class InMemoryConsolidationGroupRepository extends ConsolidationGroupRepository:
    val store: mutable.Map[ConsolidationGroupId, ConsolidationGroup] = mutable.Map.empty
    val events: mutable.ListBuffer[ConsolidationGroupEvent] = mutable.ListBuffer.empty
    def findById(id: ConsolidationGroupId): Option[ConsolidationGroup] = store.get(id)
    def findByWaveId(waveId: WaveId): List[ConsolidationGroup] =
      store.values.filter(_.waveId == waveId).toList
    def save(consolidationGroup: ConsolidationGroup, event: ConsolidationGroupEvent): Unit =
      store(consolidationGroup.id) = consolidationGroup
      events += event
    def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit =
      entries.foreach { (consolidationGroup, event) => save(consolidationGroup, event) }

  def buildService(
      transportOrderRepository: TransportOrderRepository = InMemoryTransportOrderRepository(),
      handlingUnitRepository: HandlingUnitRepository = InMemoryHandlingUnitRepository(),
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository()
  ): TransportOrderConfirmationService =
    TransportOrderConfirmationService(
      transportOrderRepository,
      handlingUnitRepository,
      taskRepository,
      consolidationGroupRepository
    )

  describe("TransportOrderConfirmationService"):
    describe("when transport order does not exist"):
      it("returns TransportOrderNotFound"):
        val missingId = TransportOrderId()
        val service = buildService()
        assert(
          service.confirm(missingId, at).left.value ==
            TransportOrderConfirmationError.TransportOrderNotFound(missingId)
        )

    describe("when transport order is not Pending"):
      it("rejects Confirmed"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val confirmed = confirmedTransportOrder()
        transportOrderRepository.store(confirmed.id) = confirmed
        val service = buildService(transportOrderRepository = transportOrderRepository)
        assert(
          service.confirm(confirmed.id, at).left.value ==
            TransportOrderConfirmationError.TransportOrderNotPending(confirmed.id)
        )

      it("rejects Cancelled"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val cancelled = cancelledTransportOrder()
        transportOrderRepository.store(cancelled.id) = cancelled
        val service = buildService(transportOrderRepository = transportOrderRepository)
        assert(
          service.confirm(cancelled.id, at).left.value ==
            TransportOrderConfirmationError.TransportOrderNotPending(cancelled.id)
        )

    describe("when handling unit does not exist"):
      it("returns HandlingUnitNotFound"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        val service = buildService(transportOrderRepository = transportOrderRepository)
        assert(
          service.confirm(pending.id, at).left.value ==
            TransportOrderConfirmationError.HandlingUnitNotFound(handlingUnitId)
        )

    describe("when handling unit is not PickCreated"):
      it("rejects InBuffer"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = inBufferHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        assert(
          service.confirm(pending.id, at).left.value ==
            TransportOrderConfirmationError.HandlingUnitNotPickCreated(handlingUnitId)
        )

      it("rejects Empty"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = emptyHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        assert(
          service.confirm(pending.id, at).left.value ==
            TransportOrderConfirmationError.HandlingUnitNotPickCreated(handlingUnitId)
        )

    describe("confirming"):
      it("confirms transport order and moves handling unit to buffer"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        val result = service.confirm(pending.id, at).value
        assert(result.confirmed.id == pending.id)
        assert(result.handlingUnit.id == handlingUnitId)
        assert(result.handlingUnit.currentLocation == bufferLocationId)

      it("includes handling unit and destination in confirmed event"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        val result = service.confirm(pending.id, at).value
        assert(result.confirmedEvent.handlingUnitId == handlingUnitId)
        assert(result.confirmedEvent.destination == bufferLocationId)
        assert(result.confirmedEvent.occurredAt == at)

      it("includes buffer location in handling unit event"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        val result = service.confirm(pending.id, at).value
        assert(result.handlingUnitEvent.locationId == bufferLocationId)
        assert(result.handlingUnitEvent.occurredAt == at)

      it("persists Confirmed transport order"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        service.confirm(pending.id, at)
        assert(
          transportOrderRepository.store(pending.id).isInstanceOf[TransportOrder.Confirmed]
        )

      it("persists InBuffer handling unit"):
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val handlingUnitRepository = InMemoryHandlingUnitRepository()
        val pending = pendingTransportOrder()
        transportOrderRepository.store(pending.id) = pending
        handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
        val service = buildService(
          transportOrderRepository = transportOrderRepository,
          handlingUnitRepository = handlingUnitRepository
        )
        service.confirm(pending.id, at)
        assert(handlingUnitRepository.store(handlingUnitId).isInstanceOf[HandlingUnit.InBuffer])

    describe("buffer completion cascade"):
      describe("when all handling units arrive"):
        it("transitions consolidation group to ReadyForWorkstation"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val taskRepository = InMemoryTaskRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          val task = completedTask()
          taskRepository.store(task.id) = task
          val consolidationGroup = pickedConsolidationGroup()
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository,
            taskRepository = taskRepository,
            consolidationGroupRepository = consolidationGroupRepository
          )
          val result = service.confirm(pending.id, at).value
          val (readyConsolidationGroup, consolidationGroupEvent) = result.bufferCompletion.value
          assert(readyConsolidationGroup.id == consolidationGroup.id)
          assert(
            consolidationGroupRepository
              .store(consolidationGroup.id)
              .isInstanceOf[ConsolidationGroup.ReadyForWorkstation]
          )

      describe("when other handling units are not yet in buffer"):
        it("does not transition consolidation group"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val taskRepository = InMemoryTaskRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val otherHandlingUnitId = HandlingUnitId()
          val otherOrderId = OrderId()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          handlingUnitRepository.store(otherHandlingUnitId) =
            pickCreatedHandlingUnit(id = otherHandlingUnitId)
          val task = completedTask()
          val otherTask =
            completedTask(handlingUnitId = otherHandlingUnitId, orderId = otherOrderId)
          taskRepository.store(task.id) = task
          taskRepository.store(otherTask.id) = otherTask
          val consolidationGroup =
            pickedConsolidationGroup(orderIds = List(orderId, otherOrderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository,
            taskRepository = taskRepository,
            consolidationGroupRepository = consolidationGroupRepository
          )
          val result = service.confirm(pending.id, at).value
          assert(result.bufferCompletion.isEmpty)

      describe("when context is missing"):
        it("skips when no task found for handling unit"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository
          )
          val result = service.confirm(pending.id, at).value
          assert(result.bufferCompletion.isEmpty)

        it("skips when task has no wave"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val taskRepository = InMemoryTaskRepository()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          val nonWaveTask = completedTask(waveId = None)
          taskRepository.store(nonWaveTask.id) = nonWaveTask
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository,
            taskRepository = taskRepository
          )
          val result = service.confirm(pending.id, at).value
          assert(result.bufferCompletion.isEmpty)

        it("skips when no consolidation group found for wave"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val taskRepository = InMemoryTaskRepository()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          val task = completedTask()
          taskRepository.store(task.id) = task
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository,
            taskRepository = taskRepository
          )
          val result = service.confirm(pending.id, at).value
          assert(result.bufferCompletion.isEmpty)

        it("skips when consolidation group is not in Picked state"):
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val handlingUnitRepository = InMemoryHandlingUnitRepository()
          val taskRepository = InMemoryTaskRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val pending = pendingTransportOrder()
          transportOrderRepository.store(pending.id) = pending
          handlingUnitRepository.store(handlingUnitId) = pickCreatedHandlingUnit()
          val task = completedTask()
          taskRepository.store(task.id) = task
          val consolidationGroup =
            ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service = buildService(
            transportOrderRepository = transportOrderRepository,
            handlingUnitRepository = handlingUnitRepository,
            taskRepository = taskRepository,
            consolidationGroupRepository = consolidationGroupRepository
          )
          val result = service.confirm(pending.id, at).value
          assert(result.bufferCompletion.isEmpty)
