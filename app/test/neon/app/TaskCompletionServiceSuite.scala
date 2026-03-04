package neon.app

import neon.common.{
  GroupId,
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
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository, TaskType}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}
import neon.wave.{OrderGrouping, Wave, WaveEvent, WaveRepository}
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class TaskCompletionServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val skuId = SkuId()
  val userId = UserId()
  val orderId = OrderId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def assignedTask(
      id: TaskId = TaskId(),
      requestedQty: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Assigned =
    Task.Assigned(
      id,
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      requestedQty,
      orderId,
      waveId,
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId,
      userId
    )

  def releasedWave(id: WaveId = waveId): Wave.Released =
    Wave.Released(id, OrderGrouping.Single, List(orderId))

  def createdConsolidationGroup(
      waveId: WaveId = waveId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.Created =
    ConsolidationGroup.Created(GroupId(), waveId, orderIds)

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

  class InMemoryWaveRepository extends WaveRepository:
    val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
    val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
    def findById(id: WaveId): Option[Wave] = store.get(id)
    def save(wave: Wave, event: WaveEvent): Unit =
      store(wave.id) = wave
      events += event

  class InMemoryConsolidationGroupRepository extends ConsolidationGroupRepository:
    val store: mutable.Map[GroupId, ConsolidationGroup] = mutable.Map.empty
    val events: mutable.ListBuffer[ConsolidationGroupEvent] = mutable.ListBuffer.empty
    def findById(id: GroupId): Option[ConsolidationGroup] = store.get(id)
    def findByWaveId(waveId: WaveId): List[ConsolidationGroup] =
      store.values.filter(_.waveId == waveId).toList
    def save(consolidationGroup: ConsolidationGroup, event: ConsolidationGroupEvent): Unit =
      store(consolidationGroup.id) = consolidationGroup
      events += event
    def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit =
      entries.foreach { (consolidationGroup, event) => save(consolidationGroup, event) }

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
      entries.foreach { (order, event) => save(order, event) }

  def buildService(
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      waveRepository: WaveRepository = InMemoryWaveRepository(),
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository(),
      transportOrderRepository: TransportOrderRepository = InMemoryTransportOrderRepository(),
      verificationProfile: VerificationProfile = VerificationProfile.disabled
  ): TaskCompletionService =
    TaskCompletionService(
      taskRepository,
      waveRepository,
      consolidationGroupRepository,
      transportOrderRepository,
      verificationProfile
    )

  describe("TaskCompletionService"):
    describe("when task does not exist"):
      it("returns TaskNotFound"):
        val missingId = TaskId()
        val service = buildService()
        val result = service.complete(missingId, 5, true, at)
        assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))

    describe("when task is not Assigned"):
      it("rejects Planned"):
        val taskRepository = InMemoryTaskRepository()
        val (planned, _) = Task.create(
          TaskType.Pick,
          skuId,
          PackagingLevel.Each,
          10,
          orderId,
          Some(waveId),
          None,
          None,
          at
        )
        taskRepository.store(planned.id) = planned
        val service = buildService(taskRepository = taskRepository)
        assert(
          service.complete(planned.id, 5, true, at).left.value ==
            TaskCompletionError.TaskNotAssigned(planned.id)
        )

      it("rejects Allocated"):
        val taskRepository = InMemoryTaskRepository()
        val (planned, _) = Task.create(
          TaskType.Pick,
          skuId,
          PackagingLevel.Each,
          10,
          orderId,
          Some(waveId),
          None,
          None,
          at
        )
        val (allocated, _) = planned.allocate(sourceLocationId, destinationLocationId, at)
        taskRepository.store(allocated.id) = allocated
        val service = buildService(taskRepository = taskRepository)
        assert(
          service.complete(allocated.id, 5, true, at).left.value ==
            TaskCompletionError.TaskNotAssigned(allocated.id)
        )

      it("rejects Completed"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        val (completed, _) = task.complete(10, at)
        taskRepository.store(completed.id) = completed
        val service = buildService(taskRepository = taskRepository)
        assert(
          service.complete(completed.id, 5, true, at).left.value ==
            TaskCompletionError.TaskNotAssigned(completed.id)
        )

      it("rejects Cancelled"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        val (cancelled, _) = task.cancel(at)
        taskRepository.store(cancelled.id) = cancelled
        val service = buildService(taskRepository = taskRepository)
        assert(
          service.complete(cancelled.id, 5, true, at).left.value ==
            TaskCompletionError.TaskNotAssigned(cancelled.id)
        )

    describe("when actual quantity is negative"):
      it("returns InvalidActualQty"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        assert(
          service.complete(task.id, -1, true, at).left.value ==
            TaskCompletionError.InvalidActualQty(task.id, -1)
        )

    describe("completing"):
      it("transitions to Completed with requested and actual quantities"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQty = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        val result = service.complete(task.id, 10, true, at).value
        assert(result.completed.id == task.id)
        assert(result.completed.actualQty == 10)
        assert(result.completed.requestedQty == 10)

      it("TaskCompleted event carries task identity, quantities, and timestamp"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQty = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        val result = service.complete(task.id, 10, true, at).value
        assert(result.completedEvent.taskId == task.id)
        assert(result.completedEvent.actualQty == 10)
        assert(result.completedEvent.requestedQty == 10)
        assert(result.completedEvent.occurredAt == at)

      it("persists Completed state"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQty = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        service.complete(task.id, 10, true, at)
        assert(taskRepository.store(task.id).isInstanceOf[Task.Completed])

    describe("shortpick cascade"):
      describe("when actual meets requested"):
        it("does not create a replacement task"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.shortpick.isEmpty)

      describe("when actual is less than requested"):
        it("creates Planned replacement for the unfulfilled remainder"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 7, true, at).value
          val (replacement, event) = result.shortpick.value
          assert(replacement.requestedQty == 3)
          assert(replacement.parentTaskId.value == task.id)
          assert(event.requestedQty == 3)

        it("persists replacement task"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 7, true, at).value
          val (replacement, _) = result.shortpick.value
          assert(taskRepository.store.contains(replacement.id))
          assert(taskRepository.store(replacement.id).isInstanceOf[Task.Planned])

      describe("when actual is zero"):
        it("creates replacement for the full requested quantity"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 0, true, at).value
          val (replacement, _) = result.shortpick.value
          assert(replacement.requestedQty == 10)

    describe("routing cascade"):
      describe("when task has a handling unit"):
        it("creates Pending transport order to the destination"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(handlingUnitId = Some(handlingUnitId))
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, true, at).value
          val (pending, event) = result.transportOrder.value
          assert(pending.handlingUnitId == handlingUnitId)
          assert(pending.destination == destinationLocationId)
          assert(event.handlingUnitId == handlingUnitId)

        it("persists transport order"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(handlingUnitId = Some(handlingUnitId))
          taskRepository.store(task.id) = task
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val service = buildService(
            taskRepository = taskRepository,
            transportOrderRepository = transportOrderRepository
          )
          val result = service.complete(task.id, 10, true, at).value
          val (pending, _) = result.transportOrder.value
          assert(transportOrderRepository.store.contains(pending.id))

      describe("when task has no handling unit"):
        it("does not create a transport order"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(handlingUnitId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.transportOrder.isEmpty)

    describe("wave completion"):
      describe("when all wave tasks are terminal"):
        it("transitions wave to Completed"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result = service.complete(task.id, 10, true, at).value
          val (completedWave, waveEvent) = result.waveCompletion.value
          assert(completedWave.id == waveId)
          assert(waveEvent.waveId == waveId)

        it("persists Completed wave"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          service.complete(task.id, 10, true, at)
          assert(waveRepository.store(waveId).isInstanceOf[Wave.Completed])

      describe("when open wave tasks remain"):
        it("does not complete the wave"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          taskRepository.store(otherTask.id) = otherTask
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.waveCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(waveId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.waveCompletion.isEmpty)

      describe("when wave is already Completed"):
        it("skips wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val completedWave = Wave.Completed(waveId, OrderGrouping.Single)
          waveRepository.store(completedWave.id) = completedWave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.waveCompletion.isEmpty)

      describe("when wave does not exist"):
        it("skips wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.waveCompletion.isEmpty)

      describe("when shortpick creates a replacement"):
        it("prevents wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(requestedQty = 10, waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result = service.complete(task.id, 7, true, at).value
          assert(result.shortpick.isDefined)
          assert(result.waveCompletion.isEmpty)

    describe("picking completion"):
      describe("when all consolidation group tasks are terminal"):
        it("transitions consolidation group to Picked"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          val (picked, consolidationGroupEvent) = result.pickingCompletion.value
          assert(picked.id == consolidationGroup.id)
          assert(consolidationGroupEvent.groupId == consolidationGroup.id)

        it("persists Picked consolidation group"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          service.complete(task.id, 10, true, at)
          assert(
            consolidationGroupRepository
              .store(consolidationGroup.id)
              .isInstanceOf[ConsolidationGroup.Picked]
          )

      describe("when open consolidation group tasks remain"):
        it("does not transition consolidation group"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          taskRepository.store(otherTask.id) = otherTask
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(waveId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when no consolidation group exists for the wave"):
        it("skips picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when consolidation group is already Picked"):
        it("skips picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val (picked, _) = consolidationGroup.pick(at)
          consolidationGroupRepository.store(picked.id) = picked
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when consolidation group does not contain the task's order"):
        it("skips picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val differentOrderId = OrderId()
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(differentOrderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when wave has tasks for multiple orders"):
        it("transitions consolidation group independently of other orders' tasks"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val otherOrderId = OrderId()
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(orderId = otherOrderId, waveId = Some(waveId))
          taskRepository.store(task.id) = task
          taskRepository.store(otherTask.id) = otherTask
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.waveCompletion.isEmpty, "wave should NOT complete — otherTask still open")
          val (picked, _) = result.pickingCompletion.value
          assert(picked.id == consolidationGroup.id)

    describe("full cascade"):
      describe("when all conditions are met"):
        it("fires completion, routing, wave, and picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val task = assignedTask(
            requestedQty = 10,
            waveId = Some(waveId),
            handlingUnitId = Some(handlingUnitId)
          )
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository,
              transportOrderRepository = transportOrderRepository
            )
          val result = service.complete(task.id, 10, true, at).value
          assert(result.completed.actualQty == 10)
          assert(result.shortpick.isEmpty)
          assert(result.transportOrder.isDefined)
          assert(result.waveCompletion.isDefined)
          assert(result.pickingCompletion.isDefined)

      describe("when shortpick occurs"):
        it("prevents wave and consolidation group completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val transportOrderRepository = InMemoryTransportOrderRepository()
          val task = assignedTask(
            requestedQty = 10,
            waveId = Some(waveId),
            handlingUnitId = Some(handlingUnitId)
          )
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val consolidationGroup =
            createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(
              taskRepository = taskRepository,
              waveRepository = waveRepository,
              consolidationGroupRepository = consolidationGroupRepository,
              transportOrderRepository = transportOrderRepository
            )
          val result = service.complete(task.id, 7, true, at).value
          assert(result.shortpick.isDefined)
          assert(result.transportOrder.isDefined)
          assert(result.waveCompletion.isEmpty)
          assert(result.pickingCompletion.isEmpty)

    describe("verification gate"):
      val eachRequired = VerificationProfile(Set(PackagingLevel.Each))

      describe("when verification is required and provided"):
        it("completes normally"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          val result = service.complete(task.id, 10, true, at).value
          assert(result.completed.id == task.id)

      describe("when verification is required and not provided"):
        it("returns VerificationRequired"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          val result = service.complete(task.id, 10, false, at)
          assert(result.left.value == TaskCompletionError.VerificationRequired(task.id))

        it("does not persist any state change"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          service.complete(task.id, 10, false, at)
          assert(taskRepository.store(task.id).isInstanceOf[Task.Assigned])

      describe("when verification is not required"):
        it("completes regardless of verified flag"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result = service.complete(task.id, 10, false, at).value
          assert(result.completed.id == task.id)

      describe("when profile targets a different packaging level"):
        it("does not gate tasks with a non-matching level"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQty = 10)
          taskRepository.store(task.id) = task
          val palletOnly = VerificationProfile(Set(PackagingLevel.Pallet))
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = palletOnly)
          val result = service.complete(task.id, 10, false, at).value
          assert(result.completed.id == task.id)

      describe("interaction with shortpick"):
        it("rejection prevents the entire cascade"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(requestedQty = 10, waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service = buildService(
            taskRepository = taskRepository,
            waveRepository = waveRepository,
            verificationProfile = eachRequired
          )
          val result = service.complete(task.id, 7, false, at)
          assert(result.left.value == TaskCompletionError.VerificationRequired(task.id))
          assert(taskRepository.store(task.id).isInstanceOf[Task.Assigned])
          assert(taskRepository.events.isEmpty)
