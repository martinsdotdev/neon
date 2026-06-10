package neon.core

import neon.common.{OrderId, PackagingLevel, TaskId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupRepository}
import neon.stockposition.StockPositionRepository
import neon.task.{Task, TaskRepository, TaskType}
import neon.transportorder.TransportOrderRepository
import neon.wave.{OrderGrouping, Wave, WaveRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

class TaskCompletionServiceSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues
    with DomainFactories:

  def buildService(
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      waveRepository: WaveRepository = InMemoryWaveRepository(),
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository(),
      transportOrderRepository: TransportOrderRepository = InMemoryTransportOrderRepository(),
      verificationProfile: VerificationProfile = VerificationProfile.disabled,
      stockPositionRepository: Option[StockPositionRepository] = None
  ): TaskCompletionService =
    TaskCompletionService(
      taskRepository = taskRepository,
      waveRepository = waveRepository,
      consolidationGroupRepository = consolidationGroupRepository,
      transportOrderRepository = transportOrderRepository,
      verificationProfile = verificationProfile,
      stockPositionRepository = stockPositionRepository
    )

  describe("TaskCompletionService"):
    describe("when task does not exist"):
      it("returns TaskNotFound"):
        val missingId = TaskId()
        val service = buildService()
        val result =
          service.complete(taskId = missingId, actualQuantity = 5, verified = true, at = at)
        assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))

    describe("when task is not Assigned"):
      it("rejects Planned"):
        val taskRepository = InMemoryTaskRepository()
        val (planned, _) = Task.create(
          taskType = TaskType.Pick,
          skuId = skuId,
          packagingLevel = PackagingLevel.Each,
          requestedQuantity = 10,
          orderId = orderId,
          waveId = Some(waveId),
          parentTaskId = None,
          handlingUnitId = None,
          at = at
        )
        taskRepository.store(planned.id) = planned
        val service = buildService(taskRepository = taskRepository)
        assert(
          service
            .complete(taskId = planned.id, actualQuantity = 5, verified = true, at = at)
            .left
            .value ==
            TaskCompletionError.TaskNotAssigned(planned.id)
        )

      it("rejects Allocated"):
        val taskRepository = InMemoryTaskRepository()
        val (planned, _) = Task.create(
          taskType = TaskType.Pick,
          skuId = skuId,
          packagingLevel = PackagingLevel.Each,
          requestedQuantity = 10,
          orderId = orderId,
          waveId = Some(waveId),
          parentTaskId = None,
          handlingUnitId = None,
          at = at
        )
        val (allocated, _) = planned.allocate(
          sourceLocationId = sourceLocationId,
          destinationLocationId = destinationLocationId,
          at = at
        )
        taskRepository.store(allocated.id) = allocated
        val service = buildService(taskRepository = taskRepository)
        assert(
          service
            .complete(taskId = allocated.id, actualQuantity = 5, verified = true, at = at)
            .left
            .value ==
            TaskCompletionError.TaskNotAssigned(allocated.id)
        )

      it("rejects Completed"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        val (completed, _) = task.complete(actualQuantity = 10, at = at)
        taskRepository.store(completed.id) = completed
        val service = buildService(taskRepository = taskRepository)
        assert(
          service
            .complete(taskId = completed.id, actualQuantity = 5, verified = true, at = at)
            .left
            .value ==
            TaskCompletionError.TaskNotAssigned(completed.id)
        )

      it("rejects Cancelled"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        val (cancelled, _) = task.cancel(at)
        taskRepository.store(cancelled.id) = cancelled
        val service = buildService(taskRepository = taskRepository)
        assert(
          service
            .complete(taskId = cancelled.id, actualQuantity = 5, verified = true, at = at)
            .left
            .value ==
            TaskCompletionError.TaskNotAssigned(cancelled.id)
        )

    describe("when actual quantity is negative"):
      it("returns InvalidActualQuantity"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask()
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        assert(
          service
            .complete(taskId = task.id, actualQuantity = -1, verified = true, at = at)
            .left
            .value ==
            TaskCompletionError.InvalidActualQuantity(taskId = task.id, actualQuantity = -1)
        )

    describe("completing"):
      it("transitions to Completed with requested and actual quantities"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQuantity = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        val result =
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
        assert(result.completed.id == task.id)
        assert(result.completed.actualQuantity == 10)
        assert(result.completed.requestedQuantity == 10)

      it("TaskCompleted event carries task identity, quantities, and timestamp"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQuantity = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        val result =
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
        assert(result.completedEvent.taskId == task.id)
        assert(result.completedEvent.actualQuantity == 10)
        assert(result.completedEvent.requestedQuantity == 10)
        assert(result.completedEvent.occurredAt == at)

      it("persists Completed state"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQuantity = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
        assert(taskRepository.store(task.id).isInstanceOf[Task.Completed])

    describe("shortpick cascade"):
      describe("when actual meets requested"):
        it("does not create a replacement task"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.shortpick.isEmpty)

      describe("when actual is less than requested"):
        it("creates Planned replacement for the unfulfilled remainder"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at).value
          val (replacement, event) = result.shortpick.value
          assert(replacement.requestedQuantity == 3)
          assert(replacement.parentTaskId.value == task.id)
          assert(event.requestedQuantity == 3)

        it("persists replacement task"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at).value
          val (replacement, _) = result.shortpick.value
          assert(taskRepository.store.contains(replacement.id))
          assert(taskRepository.store(replacement.id).isInstanceOf[Task.Planned])

      describe("when actual is zero"):
        it("creates replacement for the full requested quantity"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 0, verified = true, at = at).value
          val (replacement, _) = result.shortpick.value
          assert(replacement.requestedQuantity == 10)

    describe("routing cascade"):
      describe("when task has a handling unit"):
        it("creates Pending transport order to the destination"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(handlingUnitId = Some(handlingUnitId))
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          val (pending, _) = result.transportOrder.value
          assert(transportOrderRepository.store.contains(pending.id))

      describe("when task has no handling unit"):
        it("does not create a transport order"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(handlingUnitId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.waveCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(waveId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.waveCompletion.isEmpty)

      describe("when wave does not exist"):
        it("skips wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.waveCompletion.isEmpty)

      describe("when shortpick creates a replacement"):
        it("prevents wave completion"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(requestedQuantity = 10, waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service =
            buildService(taskRepository = taskRepository, waveRepository = waveRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          val (picked, consolidationGroupEvent) = result.pickingCompletion.value
          assert(picked.id == consolidationGroup.id)
          assert(consolidationGroupEvent.consolidationGroupId == consolidationGroup.id)

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
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.pickingCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips picking completion"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(waveId = None)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
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
            requestedQuantity = 10,
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.completed.actualQuantity == 10)
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
            requestedQuantity = 10,
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
          val result =
            service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at).value
          assert(result.shortpick.isDefined)
          assert(result.transportOrder.isDefined)
          assert(result.waveCompletion.isEmpty)
          assert(result.pickingCompletion.isEmpty)

    describe("stock consumption"):
      it("consumes allocated stock on task completion"):
        val taskRepository = InMemoryTaskRepository()
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        stockPositionRepository.store(sp.id) = sp
        val task =
          assignedTask(requestedQuantity = 10, stockPositionId = Some(sp.id))
        taskRepository.store(task.id) = task
        val service = buildService(
          taskRepository = taskRepository,
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
        val updatedPosition = stockPositionRepository.store(sp.id)
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 90)
        assert(result.stockConsumption.isDefined)

      it("consumes actual and deallocates remainder on partial pick"):
        val taskRepository = InMemoryTaskRepository()
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        stockPositionRepository.store(sp.id) = sp
        val task =
          assignedTask(requestedQuantity = 10, stockPositionId = Some(sp.id))
        taskRepository.store(task.id) = task
        val service = buildService(
          taskRepository = taskRepository,
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at).value
        val updatedPosition = stockPositionRepository.store(sp.id)
        // 7 consumed from allocated, 3 deallocated back to available
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 93)
        assert(updatedPosition.availableQuantity == 93)

      it("skips consumption when task has no stockPositionId"):
        val taskRepository = InMemoryTaskRepository()
        val stockPositionRepository = InMemoryStockPositionRepository()
        val task = assignedTask(requestedQuantity = 10, stockPositionId = None)
        taskRepository.store(task.id) = task
        val service = buildService(
          taskRepository = taskRepository,
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
        assert(result.stockConsumption.isEmpty)

      it("skips consumption when stock repository is not provided"):
        val taskRepository = InMemoryTaskRepository()
        val sp = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        val task =
          assignedTask(requestedQuantity = 10, stockPositionId = Some(sp.id))
        taskRepository.store(task.id) = task
        val service =
          buildService(taskRepository = taskRepository, stockPositionRepository = None)
        val result =
          service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
        assert(result.stockConsumption.isEmpty)

      it("deallocates full quantity when actual quantity is zero"):
        val taskRepository = InMemoryTaskRepository()
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        stockPositionRepository.store(sp.id) = sp
        val task =
          assignedTask(requestedQuantity = 10, stockPositionId = Some(sp.id))
        taskRepository.store(task.id) = task
        val service = buildService(
          taskRepository = taskRepository,
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.complete(taskId = task.id, actualQuantity = 0, verified = true, at = at).value
        val updatedPosition = stockPositionRepository.store(sp.id)
        // All 10 deallocated back to available, nothing consumed
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 100)
        assert(updatedPosition.availableQuantity == 100)
        assert(result.stockConsumption.isDefined)

    describe("verification gate"):
      val eachRequired = VerificationProfile(Set(PackagingLevel.Each))

      describe("when verification is required and provided"):
        it("completes normally"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at).value
          assert(result.completed.id == task.id)

      describe("when verification is required and not provided"):
        it("returns VerificationRequired"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = false, at = at)
          assert(result.left.value == TaskCompletionError.VerificationRequired(task.id))

        it("does not persist any state change"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
          service.complete(taskId = task.id, actualQuantity = 10, verified = false, at = at)
          assert(taskRepository.store(task.id).isInstanceOf[Task.Assigned])

      describe("when verification is not required"):
        it("completes regardless of verified flag"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val service = buildService(taskRepository = taskRepository)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = false, at = at).value
          assert(result.completed.id == task.id)

      describe("when profile targets a different packaging level"):
        it("does not gate tasks with a non-matching level"):
          val taskRepository = InMemoryTaskRepository()
          val task = assignedTask(requestedQuantity = 10)
          taskRepository.store(task.id) = task
          val palletOnly = VerificationProfile(Set(PackagingLevel.Pallet))
          val service =
            buildService(taskRepository = taskRepository, verificationProfile = palletOnly)
          val result =
            service.complete(taskId = task.id, actualQuantity = 10, verified = false, at = at).value
          assert(result.completed.id == task.id)

      describe("interaction with shortpick"):
        it("rejection prevents the entire cascade"):
          val taskRepository = InMemoryTaskRepository()
          val waveRepository = InMemoryWaveRepository()
          val task = assignedTask(requestedQuantity = 10, waveId = Some(waveId))
          taskRepository.store(task.id) = task
          val wave = releasedWave()
          waveRepository.store(wave.id) = wave
          val service = buildService(
            taskRepository = taskRepository,
            waveRepository = waveRepository,
            verificationProfile = eachRequired
          )
          val result =
            service.complete(taskId = task.id, actualQuantity = 7, verified = false, at = at)
          assert(result.left.value == TaskCompletionError.VerificationRequired(task.id))
          assert(taskRepository.store(task.id).isInstanceOf[Task.Assigned])
          assert(taskRepository.events.isEmpty)
