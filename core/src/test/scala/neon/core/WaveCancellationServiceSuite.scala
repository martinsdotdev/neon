package neon.core

import neon.common.WaveId
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupRepository}
import neon.task.{Task, TaskRepository}
import neon.transportorder.{TransportOrder, TransportOrderRepository}
import neon.wave.{Wave, WaveRepository}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

class WaveCancellationServiceSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues
    with DomainFactories:

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
