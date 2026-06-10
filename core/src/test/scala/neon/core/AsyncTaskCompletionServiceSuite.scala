package neon.core

import neon.common.{TaskId, WaveId}
import neon.consolidationgroup.ConsolidationGroup
import neon.stockposition.StockPositionEvent
import neon.task.Task
import neon.wave.Wave
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

import scala.concurrent.{ExecutionContext, Future}

/** Async shell suite. The decision scenarios live in [[TaskCompletionCascadeSuite]]; this suite
  * pins the wiring the original async service got wrong: stock consumption (which the production
  * path silently skipped) and decision consistency under stale projection-backed reads.
  */
class AsyncTaskCompletionServiceSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues
    with ScalaFutures
    with DomainFactories:

  given ExecutionContext = ExecutionContext.global

  class Fixture(staleWaveView: Option[List[Task]] = None):
    val recorder = CallRecorder()
    val taskRepository: InMemoryAsyncTaskRepository = staleWaveView match
      case None            => InMemoryAsyncTaskRepository(recorder)
      case Some(staleView) =>
        new InMemoryAsyncTaskRepository(recorder):
          override def findByWaveId(waveId: WaveId): Future[List[Task]] =
            Future.successful(staleView)
    val waveRepository = InMemoryAsyncWaveRepository(recorder)
    val consolidationGroupRepository = InMemoryAsyncConsolidationGroupRepository(recorder)
    val transportOrderRepository = InMemoryAsyncTransportOrderRepository(recorder)
    val stockPositionRepository = InMemoryAsyncStockPositionRepository(recorder)
    val service = AsyncTaskCompletionService(
      taskRepository = taskRepository,
      waveRepository = waveRepository,
      consolidationGroupRepository = consolidationGroupRepository,
      transportOrderRepository = transportOrderRepository,
      stockPositionRepository = stockPositionRepository,
      verificationProfile = VerificationProfile.disabled
    )

  describe("AsyncTaskCompletionService"):
    describe("stock consumption"):
      it("consumes allocated stock on task completion"):
        val fixture = Fixture()
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        fixture.stockPositionRepository.store(stockPosition.id) = stockPosition
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        fixture.taskRepository.store(task.id) = task
        val result = fixture.service
          .complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
          .futureValue
          .value
        val updatedPosition = fixture.stockPositionRepository.store(stockPosition.id)
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 90)
        assert(result.stockConsumption.isDefined)

      it("consumes actual and deallocates remainder in order on partial pick"):
        val fixture = Fixture()
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        fixture.stockPositionRepository.store(stockPosition.id) = stockPosition
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        fixture.taskRepository.store(task.id) = task
        fixture.service
          .complete(taskId = task.id, actualQuantity = 7, verified = true, at = at)
          .futureValue
        val stockEvents = fixture.stockPositionRepository.events.toList
        assert(stockEvents.size == 2)
        assert(stockEvents.head.isInstanceOf[StockPositionEvent.AllocatedConsumed])
        assert(stockEvents.last.isInstanceOf[StockPositionEvent.Deallocated])
        val updatedPosition = fixture.stockPositionRepository.store(stockPosition.id)
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 93)
        assert(updatedPosition.availableQuantity == 93)

      it("deallocates full quantity when actual quantity is zero"):
        val fixture = Fixture()
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        fixture.stockPositionRepository.store(stockPosition.id) = stockPosition
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        fixture.taskRepository.store(task.id) = task
        fixture.service
          .complete(taskId = task.id, actualQuantity = 0, verified = true, at = at)
          .futureValue
        val updatedPosition = fixture.stockPositionRepository.store(stockPosition.id)
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 100)
        assert(updatedPosition.availableQuantity == 100)

      it("skips the stock load when the task has no stockPositionId"):
        val fixture = Fixture()
        val task = assignedTask(requestedQuantity = 10, stockPositionId = None)
        fixture.taskRepository.store(task.id) = task
        fixture.service
          .complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
          .futureValue
        assert(!fixture.recorder.contains("stockPosition.findById"))

    describe("decision consistency under stale projection reads"):
      it("does not complete the wave or group on shortpick even when the stale view misses the replacement"):
        val task = assignedTask(requestedQuantity = 10, waveId = Some(waveId))
        // The projection-backed findByWaveId returns the completing task still Assigned and
        // cannot contain the replacement created in this request.
        val fixture = Fixture(staleWaveView = Some(List(task)))
        fixture.taskRepository.store(task.id) = task
        val wave = releasedWave()
        fixture.waveRepository.store(wave.id) = wave
        val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
        fixture.consolidationGroupRepository.store(group.id) = group
        val result = fixture.service
          .complete(taskId = task.id, actualQuantity = 7, verified = true, at = at)
          .futureValue
          .value
        assert(result.shortpick.isDefined)
        assert(result.waveCompletion.isEmpty)
        assert(result.pickingCompletion.isEmpty)
        assert(fixture.waveRepository.store(waveId).isInstanceOf[Wave.Released])
        assert(
          fixture.consolidationGroupRepository
            .store(group.id)
            .isInstanceOf[ConsolidationGroup.Created]
        )

      it("completes the wave and group on full pick even when the stale view shows the task as Assigned"):
        val task = assignedTask(requestedQuantity = 10, waveId = Some(waveId))
        val fixture = Fixture(staleWaveView = Some(List(task)))
        fixture.taskRepository.store(task.id) = task
        val wave = releasedWave()
        fixture.waveRepository.store(wave.id) = wave
        val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
        fixture.consolidationGroupRepository.store(group.id) = group
        val result = fixture.service
          .complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
          .futureValue
          .value
        assert(result.waveCompletion.isDefined)
        assert(result.pickingCompletion.isDefined)
        assert(fixture.waveRepository.store(waveId).isInstanceOf[Wave.Completed])
        assert(
          fixture.consolidationGroupRepository
            .store(group.id)
            .isInstanceOf[ConsolidationGroup.Picked]
        )

    describe("conditional loads"):
      it("skips wave, task, and group loads when the task has no wave"):
        val fixture = Fixture()
        val task = assignedTask(waveId = None)
        fixture.taskRepository.store(task.id) = task
        fixture.service
          .complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
          .futureValue
        assert(!fixture.recorder.contains("wave.findById"))
        assert(!fixture.recorder.contains("task.findByWaveId"))
        assert(!fixture.recorder.contains("consolidationGroup.findByWaveId"))

    describe("save ordering"):
      it("persists task, stock writes, shortpick, and transport order in cascade order"):
        val fixture = Fixture()
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        fixture.stockPositionRepository.store(stockPosition.id) = stockPosition
        val task = assignedTask(
          requestedQuantity = 10,
          handlingUnitId = Some(handlingUnitId),
          stockPositionId = Some(stockPosition.id)
        )
        fixture.taskRepository.store(task.id) = task
        fixture.service
          .complete(taskId = task.id, actualQuantity = 7, verified = true, at = at)
          .futureValue
        assert(
          fixture.recorder.saves == List(
            "task.save",
            "stockPosition.save",
            "stockPosition.save",
            "task.save",
            "transportOrder.save"
          )
        )

      it("persists wave and group completion after the transport order on full pick"):
        val fixture = Fixture()
        val task = assignedTask(waveId = Some(waveId), handlingUnitId = Some(handlingUnitId))
        fixture.taskRepository.store(task.id) = task
        val wave = releasedWave()
        fixture.waveRepository.store(wave.id) = wave
        val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
        fixture.consolidationGroupRepository.store(group.id) = group
        fixture.service
          .complete(taskId = task.id, actualQuantity = 10, verified = true, at = at)
          .futureValue
        assert(
          fixture.recorder.saves == List(
            "task.save",
            "transportOrder.save",
            "wave.save",
            "consolidationGroup.save"
          )
        )

    describe("error passthrough"):
      it("returns the validation error without persisting"):
        val fixture = Fixture()
        val missingId = TaskId()
        val result = fixture.service
          .complete(taskId = missingId, actualQuantity = 5, verified = true, at = at)
          .futureValue
        assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))
        assert(fixture.recorder.saves.isEmpty)
