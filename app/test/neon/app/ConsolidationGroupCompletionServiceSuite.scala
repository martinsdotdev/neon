package neon.app

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType, WorkstationRepository}
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class ConsolidationGroupCompletionServiceSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues:
  val waveId = WaveId()
  val orderId = OrderId()
  val groupId = GroupId()
  val workstationId = WorkstationId()
  val at = Instant.now()

  def assignedConsolidationGroup(
      id: GroupId = groupId,
      workstationId: WorkstationId = workstationId
  ): ConsolidationGroup.Assigned =
    ConsolidationGroup.Assigned(id, waveId, List(orderId), workstationId)

  def createdConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Created =
    ConsolidationGroup.Created(id, waveId, List(orderId))

  def pickedConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(id, waveId, List(orderId))

  def readyForWorkstationConsolidationGroup(
      id: GroupId = groupId
  ): ConsolidationGroup.ReadyForWorkstation =
    ConsolidationGroup.ReadyForWorkstation(id, waveId, List(orderId))

  def completedConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Completed =
    ConsolidationGroup.Completed(id, waveId, List(orderId), workstationId)

  def cancelledConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Cancelled =
    ConsolidationGroup.Cancelled(id, waveId, List(orderId))

  def activeWorkstation(
      id: WorkstationId = workstationId,
      groupId: GroupId = groupId
  ): Workstation.Active =
    Workstation.Active(id, WorkstationType.PutWall, 5, groupId)

  def idleWorkstation(id: WorkstationId = workstationId): Workstation.Idle =
    Workstation.Idle(id, WorkstationType.PutWall, 5)

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

  class InMemoryWorkstationRepository extends WorkstationRepository:
    val store: mutable.Map[WorkstationId, Workstation] = mutable.Map.empty
    val events: mutable.ListBuffer[WorkstationEvent] = mutable.ListBuffer.empty
    def findById(id: WorkstationId): Option[Workstation] = store.get(id)
    def findIdleByType(workstationType: WorkstationType): Option[Workstation.Idle] =
      store.values.collectFirst {
        case idle: Workstation.Idle if idle.workstationType == workstationType =>
          idle
      }
    def save(workstation: Workstation, event: WorkstationEvent): Unit =
      store(workstation.id) = workstation
      events += event

  def buildService(
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository(),
      workstationRepository: WorkstationRepository = InMemoryWorkstationRepository()
  ): ConsolidationGroupCompletionService =
    ConsolidationGroupCompletionService(consolidationGroupRepository, workstationRepository)

  describe("ConsolidationGroupCompletionService"):
    describe("when consolidation group does not exist"):
      it("returns ConsolidationGroupNotFound"):
        val missingId = GroupId()
        val service = buildService()
        assert(
          service.complete(missingId, at).left.value ==
            ConsolidationGroupCompletionError.ConsolidationGroupNotFound(missingId)
        )

    describe("when consolidation group is not Assigned"):
      it("rejects Created"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = createdConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError
              .ConsolidationGroupNotAssigned(consolidationGroup.id)
        )

      it("rejects Picked"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = pickedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError
              .ConsolidationGroupNotAssigned(consolidationGroup.id)
        )

      it("rejects ReadyForWorkstation"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError
              .ConsolidationGroupNotAssigned(consolidationGroup.id)
        )

      it("rejects Completed"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = completedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError
              .ConsolidationGroupNotAssigned(consolidationGroup.id)
        )

      it("rejects Cancelled"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = cancelledConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError
              .ConsolidationGroupNotAssigned(consolidationGroup.id)
        )

    describe("when workstation does not exist"):
      it("returns WorkstationNotFound"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError.WorkstationNotFound(workstationId)
        )

    describe("when workstation is not Active"):
      it("returns WorkstationNotActive"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        assert(
          service.complete(consolidationGroup.id, at).left.value ==
            ConsolidationGroupCompletionError.WorkstationNotActive(workstationId)
        )

    describe("completing"):
      it("completes consolidation group and releases workstation"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = activeWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        val result = service.complete(consolidationGroup.id, at).value
        assert(result.completed.id == consolidationGroup.id)
        assert(result.workstation.id == workstation.id)

      it("persists Completed consolidation group"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = activeWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        service.complete(consolidationGroup.id, at)
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Completed]
        )

      it("persists Idle workstation"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = activeWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        service.complete(consolidationGroup.id, at)
        assert(
          workstationRepository.store(workstation.id).isInstanceOf[Workstation.Idle]
        )

      it("result carries both aggregates and events"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = activeWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        val result = service.complete(consolidationGroup.id, at).value
        assert(result.completedEvent.groupId == consolidationGroup.id)
        assert(result.workstationEvent.workstationId == workstation.id)
