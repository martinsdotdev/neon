package neon.core

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId, WorkstationMode}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationRepository, WorkstationType}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

import java.time.Instant
import scala.collection.mutable

class ConsolidationGroupCompletionServiceSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues:
  val waveId = WaveId()
  val orderId = OrderId()
  val consolidationGroupId = ConsolidationGroupId()
  val workstationId = WorkstationId()
  val at = Instant.now()

  def assignedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId,
      workstationId: WorkstationId = workstationId
  ): ConsolidationGroup.Assigned =
    ConsolidationGroup.Assigned(id, waveId, List(orderId), workstationId)

  def createdConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Created =
    ConsolidationGroup.Created(id, waveId, List(orderId))

  def pickedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(id, waveId, List(orderId))

  def readyForWorkstationConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.ReadyForWorkstation =
    ConsolidationGroup.ReadyForWorkstation(id, waveId, List(orderId))

  def completedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Completed =
    ConsolidationGroup.Completed(id, waveId, List(orderId), workstationId)

  def cancelledConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Cancelled =
    ConsolidationGroup.Cancelled(id, waveId, List(orderId))

  def activeWorkstation(
      id: WorkstationId = workstationId,
      consolidationGroupId: ConsolidationGroupId = consolidationGroupId
  ): Workstation.Active =
    Workstation.Active(
      id = id,
      workstationType = WorkstationType.PutWall,
      slotCount = 5,
      mode = WorkstationMode.Picking,
      assignmentId = consolidationGroupId.value
    )

  def idleWorkstation(id: WorkstationId = workstationId): Workstation.Idle =
    Workstation.Idle(
      id = id,
      workstationType = WorkstationType.PutWall,
      slotCount = 5,
      mode = WorkstationMode.Picking
    )

  def buildService(
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository(),
      workstationRepository: WorkstationRepository = InMemoryWorkstationRepository()
  ): ConsolidationGroupCompletionService =
    ConsolidationGroupCompletionService(consolidationGroupRepository, workstationRepository)

  private def setupAssigned(
      workstation: Option[Workstation] = Some(activeWorkstation())
  ): (
      InMemoryConsolidationGroupRepository,
      InMemoryWorkstationRepository,
      ConsolidationGroup.Assigned,
      ConsolidationGroupCompletionService
  ) =
    val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
    val workstationRepository = InMemoryWorkstationRepository()
    val consolidationGroup = assignedConsolidationGroup()
    consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
    workstation.foreach(ws => workstationRepository.store(ws.id) = ws)
    val service = buildService(
      consolidationGroupRepository = consolidationGroupRepository,
      workstationRepository = workstationRepository
    )
    (consolidationGroupRepository, workstationRepository, consolidationGroup, service)

  describe("ConsolidationGroupCompletionService"):
    describe("when consolidation group does not exist"):
      it("returns ConsolidationGroupNotFound"):
        val missingId = ConsolidationGroupId()
        val service = buildService()
        assert(
          service.complete(missingId, at).left.value ==
            ConsolidationGroupCompletionError.ConsolidationGroupNotFound(missingId)
        )

    describe("when consolidation group is not Assigned"):
      val nonAssignableStates = List(
        "Created" -> (() => createdConsolidationGroup()),
        "Picked" -> (() => pickedConsolidationGroup()),
        "ReadyForWorkstation" -> (() => readyForWorkstationConsolidationGroup()),
        "Completed" -> (() => completedConsolidationGroup()),
        "Cancelled" -> (() => cancelledConsolidationGroup())
      )

      nonAssignableStates.foreach { case (stateName, stateFactory) =>
        it(s"rejects $stateName with ConsolidationGroupNotAssigned"):
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val consolidationGroup = stateFactory()
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(consolidationGroupRepository = consolidationGroupRepository)
          assert(
            service.complete(consolidationGroup.id, at).left.value ==
              ConsolidationGroupCompletionError.ConsolidationGroupNotAssigned(consolidationGroup.id)
          )
      }

    describe("when workstation does not exist"):
      it("returns WorkstationNotFound and leaves state untouched"):
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupAssigned(workstation = None)

        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError.WorkstationNotFound(workstationId)
        )
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Assigned]
        )
        assert(consolidationGroupRepository.events.isEmpty)
        assert(workstationRepository.events.isEmpty)

    describe("when workstation is not Active"):
      it("returns WorkstationNotActive and leaves state untouched"):
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupAssigned(workstation = Some(idleWorkstation()))

        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError.WorkstationNotActive(workstationId)
        )
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Assigned]
        )
        assert(workstationRepository.store(workstationId).isInstanceOf[Workstation.Idle])
        assert(consolidationGroupRepository.events.isEmpty)
        assert(workstationRepository.events.isEmpty)

    describe("completing"):
      it("completes consolidation group, releases workstation, and persists both"):
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupAssigned(workstation = Some(activeWorkstation()))

        val result = service.complete(consolidationGroup.id, at).value
        assert(result.completed.id == consolidationGroup.id)
        assert(result.workstation.id == workstationId)
        assert(result.completedEvent.consolidationGroupId == consolidationGroup.id)
        assert(result.workstationEvent.workstationId == workstationId)
        assert(result.completedEvent.occurredAt == at)
        assert(result.workstationEvent.occurredAt == at)
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Completed]
        )
        assert(workstationRepository.store(workstationId).isInstanceOf[Workstation.Idle])
        assert(consolidationGroupRepository.events.size == 1)
        assert(workstationRepository.events.size == 1)
