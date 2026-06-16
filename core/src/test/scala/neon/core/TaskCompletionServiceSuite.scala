package neon.core

import neon.common.{PackagingLevel, TaskId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupRepository}
import neon.stockposition.StockPositionRepository
import neon.task.{Task, TaskRepository}
import neon.transportorder.TransportOrderRepository
import neon.wave.{Wave, WaveRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

/** Shell suite: load wiring, persistence effects, and end-to-end smoke paths. The cascade decisions
  * themselves are covered by [[TaskCompletionCascadeSuite]].
  */
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
    describe("validation wiring"):
      it("returns TaskNotFound when the task does not exist"):
        val missingId = TaskId()
        val service = buildService()
        val result =
          service.complete(taskId = missingId, actualQuantity = 5, verified = true, at = at)
        assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))

    describe("persistence"):
      it("persists Completed state"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQuantity = 10)
        taskRepository.store(task.id) = task
        val service = buildService(taskRepository = taskRepository)
        service.complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
        assert(taskRepository.store(task.id).isInstanceOf[Task.Completed])

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
        service.complete(taskId = task.id, actualQuantity = 7, verified = true, at = at)
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

      it("does not persist any state change on rejection"):
        val taskRepository = InMemoryTaskRepository()
        val task = assignedTask(requestedQuantity = 10)
        taskRepository.store(task.id) = task
        val service =
          buildService(taskRepository = taskRepository, verificationProfile = eachRequired)
        service.complete(taskId = task.id, actualQuantity = 10, verified = false, at = at)
        assert(taskRepository.store(task.id).isInstanceOf[Task.Assigned])

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
