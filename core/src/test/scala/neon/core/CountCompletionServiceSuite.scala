package neon.core

import neon.common.{
  CountMethod,
  CountTaskId,
  CountType,
  CycleCountId,
  LocationId,
  SkuId,
  UserId,
  WarehouseAreaId
}
import neon.counttask.{CountTask, CountTaskEvent, CountTaskRepository}
import neon.cyclecount.{CycleCount, CycleCountEvent, CycleCountRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

import java.time.Instant
import scala.collection.mutable

class CountCompletionServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val cycleCountId = CycleCountId()
  val warehouseAreaId = WarehouseAreaId()
  val skuId = SkuId()
  val locationId = LocationId()
  val userId = UserId()
  val at = Instant.now()

  def inProgressCycleCount(
      id: CycleCountId = cycleCountId
  ): CycleCount.InProgress =
    CycleCount.InProgress(
      id,
      warehouseAreaId,
      List(skuId),
      CountType.Planned,
      CountMethod.Blind
    )

  def recordedCountTask(
      cycleCountId: CycleCountId = cycleCountId,
      expectedQuantity: Int = 50,
      actualQuantity: Int = 48,
      countedBy: UserId = userId
  ): CountTask.Recorded =
    CountTask.Recorded(
      CountTaskId(),
      cycleCountId,
      skuId,
      locationId,
      expectedQuantity,
      countedBy,
      actualQuantity,
      actualQuantity - expectedQuantity
    )

  def assignedCountTask(
      cycleCountId: CycleCountId = cycleCountId
  ): CountTask.Assigned =
    CountTask.Assigned(CountTaskId(), cycleCountId, skuId, locationId, 50, userId)

  def pendingCountTask(
      cycleCountId: CycleCountId = cycleCountId
  ): CountTask.Pending =
    CountTask.Pending(CountTaskId(), cycleCountId, skuId, locationId, 50)

  class InMemoryCycleCountRepository extends CycleCountRepository:
    val store: mutable.Map[CycleCountId, CycleCount] = mutable.Map.empty
    val events: mutable.ListBuffer[CycleCountEvent] = mutable.ListBuffer.empty
    def findById(id: CycleCountId): Option[CycleCount] = store.get(id)
    def save(cycleCount: CycleCount, event: CycleCountEvent): Unit =
      store(cycleCount.id) = cycleCount
      events += event

  class InMemoryCountTaskRepository extends CountTaskRepository:
    val store: mutable.Map[CountTaskId, CountTask] = mutable.Map.empty
    val events: mutable.ListBuffer[CountTaskEvent] = mutable.ListBuffer.empty
    def findById(id: CountTaskId): Option[CountTask] = store.get(id)
    def findByCycleCountId(cycleCountId: CycleCountId): List[CountTask] =
      store.values.filter(_.cycleCountId == cycleCountId).toList
    def save(countTask: CountTask, event: CountTaskEvent): Unit =
      store(countTask.id) = countTask
      events += event
    def saveAll(entries: List[(CountTask, CountTaskEvent)]): Unit =
      entries.foreach { (countTask, event) => save(countTask, event) }

  def buildService(
      cycleCountRepository: CycleCountRepository = InMemoryCycleCountRepository(),
      countTaskRepository: CountTaskRepository = InMemoryCountTaskRepository()
  ): CountCompletionService =
    CountCompletionService(cycleCountRepository, countTaskRepository)

  describe("CountCompletionService"):
    describe("when cycle count does not exist"):
      it("returns CycleCountNotFound"):
        val service = buildService()
        val result = service.tryComplete(CycleCountId(), at)
        assert(result.left.value.isInstanceOf[CountCompletionError.CycleCountNotFound])

    describe("when cycle count is not InProgress"):
      it("rejects Completed cycle count"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val completed = CycleCount.Completed(
          cycleCountId,
          warehouseAreaId,
          List(skuId),
          CountType.Planned,
          CountMethod.Blind
        )
        cycleCountRepository.store(cycleCountId) = completed
        val service = buildService(cycleCountRepository = cycleCountRepository)
        val result = service.tryComplete(cycleCountId, at)
        assert(result.left.value.isInstanceOf[CountCompletionError.CycleCountNotInProgress])

      it("rejects New cycle count"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val newCount = CycleCount.New(
          cycleCountId,
          warehouseAreaId,
          List(skuId),
          CountType.Planned,
          CountMethod.Blind
        )
        cycleCountRepository.store(cycleCountId) = newCount
        val service = buildService(cycleCountRepository = cycleCountRepository)
        val result = service.tryComplete(cycleCountId, at)
        assert(result.left.value.isInstanceOf[CountCompletionError.CycleCountNotInProgress])

    describe("when all count tasks are recorded"):
      it("completes the cycle count"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val task = recordedCountTask()
        countTaskRepository.store(task.id) = task
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at).value
        assert(result.completed.isInstanceOf[CycleCount.Completed])
        assert(result.completedEvent.cycleCountId == cycleCountId)

      it("persists Completed cycle count"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val task = recordedCountTask()
        countTaskRepository.store(task.id) = task
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        service.tryComplete(cycleCountId, at)
        assert(cycleCountRepository.store(cycleCountId).isInstanceOf[CycleCount.Completed])

      it("collects variances from count tasks with non-zero variance"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val taskWithVariance = recordedCountTask(expectedQuantity = 50, actualQuantity = 48)
        val taskWithoutVariance = recordedCountTask(expectedQuantity = 30, actualQuantity = 30)
        countTaskRepository.store(taskWithVariance.id) = taskWithVariance
        countTaskRepository.store(taskWithoutVariance.id) = taskWithoutVariance
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at).value
        assert(result.variances.size == 1)
        val variance = result.variances.head
        assert(variance.countTaskId == taskWithVariance.id)
        assert(variance.expectedQuantity == 50)
        assert(variance.actualQuantity == 48)
        assert(variance.variance == -2)
        assert(variance.countedBy == userId)

    describe("when some count tasks are not terminal"):
      it("returns OpenCountTasksRemaining"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val recorded = recordedCountTask()
        val assigned = assignedCountTask()
        countTaskRepository.store(recorded.id) = recorded
        countTaskRepository.store(assigned.id) = assigned
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at)
        assert(result.left.value.isInstanceOf[CountCompletionError.OpenCountTasksRemaining])

      it("pending tasks also prevent completion"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val pending = pendingCountTask()
        countTaskRepository.store(pending.id) = pending
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at)
        assert(result.left.value.isInstanceOf[CountCompletionError.OpenCountTasksRemaining])

    describe("when all count tasks are terminal (mix of recorded and cancelled)"):
      it("completes the cycle count"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val recorded = recordedCountTask()
        val cancelled =
          CountTask.Cancelled(CountTaskId(), cycleCountId, skuId, locationId, 50, None)
        countTaskRepository.store(recorded.id) = recorded
        countTaskRepository.store(cancelled.id) = cancelled
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at).value
        assert(result.completed.isInstanceOf[CycleCount.Completed])

      it("only collects variances from recorded tasks, not cancelled"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val recorded = recordedCountTask(expectedQuantity = 50, actualQuantity = 45)
        val cancelled =
          CountTask.Cancelled(CountTaskId(), cycleCountId, skuId, locationId, 50, None)
        countTaskRepository.store(recorded.id) = recorded
        countTaskRepository.store(cancelled.id) = cancelled
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at).value
        assert(result.variances.size == 1)
        assert(result.variances.head.countTaskId == recorded.id)

    describe("when no count tasks have variances"):
      it("returns empty variances list"):
        val cycleCountRepository = InMemoryCycleCountRepository()
        val countTaskRepository = InMemoryCountTaskRepository()
        val cycleCount = inProgressCycleCount()
        cycleCountRepository.store(cycleCountId) = cycleCount
        val task = recordedCountTask(expectedQuantity = 50, actualQuantity = 50)
        countTaskRepository.store(task.id) = task
        val service =
          buildService(
            cycleCountRepository = cycleCountRepository,
            countTaskRepository = countTaskRepository
          )
        val result = service.tryComplete(cycleCountId, at).value
        assert(result.variances.isEmpty)
