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
import neon.task.{Task, TaskEvent, TaskRepository, TaskType}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{OrderGrouping, Wave, WaveEvent, WaveRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

import java.time.Instant
import scala.collection.mutable

class WaveCancellationServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def plannedWave(id: WaveId = waveId): Wave.Planned =
    Wave.Planned(id, OrderGrouping.Single, List(orderId))

  def releasedWave(id: WaveId = waveId): Wave.Released =
    Wave.Released(id, OrderGrouping.Single, List(orderId))

  def completedWave(id: WaveId = waveId): Wave.Completed =
    Wave.Completed(id, OrderGrouping.Single)

  def cancelledWave(id: WaveId = waveId): Wave.Cancelled =
    Wave.Cancelled(id, OrderGrouping.Single)

  def plannedTask(
      waveId: WaveId = waveId,
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Planned =
    Task.Planned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      handlingUnitId
    )

  def allocatedTask(
      waveId: WaveId = waveId,
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Allocated =
    Task.Allocated(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId
    )

  def assignedTask(
      waveId: WaveId = waveId,
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Assigned =
    Task.Assigned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId,
      userId
    )

  def completedTask(
      waveId: WaveId = waveId,
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Completed =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      10,
      orderId,
      Some(waveId),
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId,
      userId
    )

  def cancelledTask(waveId: WaveId = waveId): Task.Cancelled =
    Task.Cancelled(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      orderId,
      Some(waveId),
      None,
      None,
      None,
      None,
      None
    )

  def pendingTransportOrder(
      handlingUnitId: HandlingUnitId = handlingUnitId
  ): TransportOrder.Pending =
    TransportOrder.Pending(TransportOrderId(), handlingUnitId, destinationLocationId)

  def confirmedTransportOrder(
      handlingUnitId: HandlingUnitId = handlingUnitId
  ): TransportOrder.Confirmed =
    TransportOrder.Confirmed(TransportOrderId(), handlingUnitId, destinationLocationId)

  def createdConsolidationGroup(waveId: WaveId = waveId): ConsolidationGroup.Created =
    ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId))

  def pickedConsolidationGroup(waveId: WaveId = waveId): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(ConsolidationGroupId(), waveId, List(orderId))

  class InMemoryWaveRepository extends WaveRepository:
    val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
    val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
    def findById(id: WaveId): Option[Wave] = store.get(id)
    def save(wave: Wave, event: WaveEvent): Unit =
      store(wave.id) = wave
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
      waveRepository: WaveRepository = InMemoryWaveRepository(),
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      transportOrderRepository: TransportOrderRepository = InMemoryTransportOrderRepository(),
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository()
  ): WaveCancellationService =
    WaveCancellationService(
      waveRepository,
      taskRepository,
      transportOrderRepository,
      consolidationGroupRepository
    )

  describe("WaveCancellationService"):
    describe("when wave does not exist"):
      it("returns WaveNotFound"):
        val missingId = WaveId()
        val service = buildService()
        val result = service.cancel(missingId, at)
        assert(result.left.value == WaveCancellationError.WaveNotFound(missingId))

    describe("when wave is already terminal"):
      it("returns WaveAlreadyTerminal for a Completed wave"):
        val waveRepository = InMemoryWaveRepository()
        waveRepository.store(waveId) = completedWave()
        val service = buildService(waveRepository = waveRepository)
        assert(
          service.cancel(waveId, at).left.value ==
            WaveCancellationError.WaveAlreadyTerminal(waveId)
        )

      it("returns WaveAlreadyTerminal for a Cancelled wave"):
        val waveRepository = InMemoryWaveRepository()
        waveRepository.store(waveId) = cancelledWave()
        val service = buildService(waveRepository = waveRepository)
        assert(
          service.cancel(waveId, at).left.value ==
            WaveCancellationError.WaveAlreadyTerminal(waveId)
        )

    describe("cancelling a Planned wave"):
      it("cancels without cascade"):
        val waveRepository = InMemoryWaveRepository()
        waveRepository.store(waveId) = plannedWave()
        val service = buildService(waveRepository = waveRepository)
        val result = service.cancel(waveId, at).value
        assert(result.cancelled.id == waveId)
        assert(result.cancelledTasks.isEmpty)
        assert(result.cancelledTransportOrders.isEmpty)
        assert(result.cancelledConsolidationGroups.isEmpty)
        assert(waveRepository.store(waveId).isInstanceOf[Wave.Cancelled])

    describe("cancelling a Released wave"):
      it("cancels the wave"):
        val waveRepository = InMemoryWaveRepository()
        waveRepository.store(waveId) = releasedWave()
        val service = buildService(waveRepository = waveRepository)
        val result = service.cancel(waveId, at).value
        assert(result.cancelled.id == waveId)
        assert(waveRepository.store(waveId).isInstanceOf[Wave.Cancelled])

      describe("task cascade"):
        it("cancels Planned, Allocated, and Assigned tasks"):
          val waveRepository = InMemoryWaveRepository()
          val taskRepository = InMemoryTaskRepository()
          waveRepository.store(waveId) = releasedWave()
          val planned = plannedTask()
          val allocated = allocatedTask()
          val assigned = assignedTask()
          taskRepository.store(planned.id) = planned
          taskRepository.store(allocated.id) = allocated
          taskRepository.store(assigned.id) = assigned
          val service =
            buildService(waveRepository = waveRepository, taskRepository = taskRepository)
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTasks.size == 3)
          assert(taskRepository.store(planned.id).isInstanceOf[Task.Cancelled])
          assert(taskRepository.store(allocated.id).isInstanceOf[Task.Cancelled])
          assert(taskRepository.store(assigned.id).isInstanceOf[Task.Cancelled])

        it("skips Completed and Cancelled tasks"):
          val waveRepository = InMemoryWaveRepository()
          val taskRepository = InMemoryTaskRepository()
          waveRepository.store(waveId) = releasedWave()
          val open = plannedTask()
          val completed = completedTask()
          val cancelled = cancelledTask()
          taskRepository.store(open.id) = open
          taskRepository.store(completed.id) = completed
          taskRepository.store(cancelled.id) = cancelled
          val service =
            buildService(waveRepository = waveRepository, taskRepository = taskRepository)
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTasks.size == 1)
          assert(result.cancelledTasks.head._1.id == open.id)
          assert(taskRepository.store(completed.id).isInstanceOf[Task.Completed])
          assert(taskRepository.store(cancelled.id).isInstanceOf[Task.Cancelled])

      describe("transport order cascade"):
        it("cancels Pending transport orders found via task handling units"):
          val waveRepository = InMemoryWaveRepository()
          val taskRepository = InMemoryTaskRepository()
          val transportOrderRepository = InMemoryTransportOrderRepository()
          waveRepository.store(waveId) = releasedWave()
          val task = plannedTask(handlingUnitId = Some(handlingUnitId))
          taskRepository.store(task.id) = task
          val pendingOrder = pendingTransportOrder(handlingUnitId)
          transportOrderRepository.store(pendingOrder.id) = pendingOrder
          val service = buildService(
            waveRepository = waveRepository,
            taskRepository = taskRepository,
            transportOrderRepository = transportOrderRepository
          )
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTransportOrders.size == 1)
          assert(result.cancelledTransportOrders.head._1.id == pendingOrder.id)
          assert(
            transportOrderRepository
              .store(pendingOrder.id)
              .isInstanceOf[TransportOrder.Cancelled]
          )

        it("skips Confirmed transport orders"):
          val waveRepository = InMemoryWaveRepository()
          val taskRepository = InMemoryTaskRepository()
          val transportOrderRepository = InMemoryTransportOrderRepository()
          waveRepository.store(waveId) = releasedWave()
          val task = plannedTask(handlingUnitId = Some(handlingUnitId))
          taskRepository.store(task.id) = task
          val confirmed = confirmedTransportOrder(handlingUnitId)
          transportOrderRepository.store(confirmed.id) = confirmed
          val service = buildService(
            waveRepository = waveRepository,
            taskRepository = taskRepository,
            transportOrderRepository = transportOrderRepository
          )
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTransportOrders.isEmpty)
          assert(
            transportOrderRepository.store(confirmed.id).isInstanceOf[TransportOrder.Confirmed]
          )

        it("skips tasks without handling units"):
          val waveRepository = InMemoryWaveRepository()
          val taskRepository = InMemoryTaskRepository()
          val transportOrderRepository = InMemoryTransportOrderRepository()
          waveRepository.store(waveId) = releasedWave()
          val taskWithoutHandlingUnit = plannedTask(handlingUnitId = None)
          taskRepository.store(taskWithoutHandlingUnit.id) = taskWithoutHandlingUnit
          val service = buildService(
            waveRepository = waveRepository,
            taskRepository = taskRepository,
            transportOrderRepository = transportOrderRepository
          )
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTransportOrders.isEmpty)

      describe("consolidation group cascade"):
        it("cancels non-terminal consolidation groups"):
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          waveRepository.store(waveId) = releasedWave()
          val consolidationGroup = createdConsolidationGroup()
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service = buildService(
            waveRepository = waveRepository,
            consolidationGroupRepository = consolidationGroupRepository
          )
          val result = service.cancel(waveId, at).value
          assert(result.cancelledConsolidationGroups.size == 1)
          assert(result.cancelledConsolidationGroups.head._1.id == consolidationGroup.id)
          assert(
            consolidationGroupRepository
              .store(consolidationGroup.id)
              .isInstanceOf[ConsolidationGroup.Cancelled]
          )

      describe("when no downstream aggregates exist"):
        it("produces empty cascade result"):
          val waveRepository = InMemoryWaveRepository()
          waveRepository.store(waveId) = releasedWave()
          val service = buildService(waveRepository = waveRepository)
          val result = service.cancel(waveId, at).value
          assert(result.cancelledTasks.isEmpty)
          assert(result.cancelledTransportOrders.isEmpty)
          assert(result.cancelledConsolidationGroups.isEmpty)

    describe("full cascade"):
      it("cancels wave, tasks, transport orders, and consolidation groups together"):
        val waveRepository = InMemoryWaveRepository()
        val taskRepository = InMemoryTaskRepository()
        val transportOrderRepository = InMemoryTransportOrderRepository()
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        waveRepository.store(waveId) = releasedWave()
        val planned = plannedTask(handlingUnitId = Some(handlingUnitId))
        val completed = completedTask()
        taskRepository.store(planned.id) = planned
        taskRepository.store(completed.id) = completed
        val pendingOrder = pendingTransportOrder(handlingUnitId)
        transportOrderRepository.store(pendingOrder.id) = pendingOrder
        val consolidationGroup = createdConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service = buildService(
          waveRepository = waveRepository,
          taskRepository = taskRepository,
          transportOrderRepository = transportOrderRepository,
          consolidationGroupRepository = consolidationGroupRepository
        )
        val result = service.cancel(waveId, at).value
        assert(result.cancelled.id == waveId)
        assert(result.cancelledTasks.size == 1)
        assert(result.cancelledTransportOrders.size == 1)
        assert(result.cancelledConsolidationGroups.size == 1)
