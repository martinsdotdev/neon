package neon.app

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType, WorkstationRepository}
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class WorkstationAssignmentServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val waveId = WaveId()
  val orderId = OrderId()
  val groupId = GroupId()
  val workstationId = WorkstationId()
  val at = Instant.now()

  def readyForWorkstationConsolidationGroup(
      id: GroupId = groupId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.ReadyForWorkstation =
    ConsolidationGroup.ReadyForWorkstation(id, waveId, orderIds)

  def createdConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Created =
    ConsolidationGroup.Created(id, waveId, List(orderId))

  def pickedConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(id, waveId, List(orderId))

  def assignedConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Assigned =
    ConsolidationGroup.Assigned(id, waveId, List(orderId), workstationId)

  def completedConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Completed =
    ConsolidationGroup.Completed(id, waveId, List(orderId), workstationId)

  def cancelledConsolidationGroup(id: GroupId = groupId): ConsolidationGroup.Cancelled =
    ConsolidationGroup.Cancelled(id, waveId, List(orderId))

  def idleWorkstation(
      id: WorkstationId = workstationId,
      slotCount: Int = 5
  ): Workstation.Idle =
    Workstation.Idle(id, WorkstationType.PutWall, slotCount)

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
  ): WorkstationAssignmentService =
    WorkstationAssignmentService(consolidationGroupRepository, workstationRepository)

  describe("WorkstationAssignmentService"):
    describe("when consolidation group does not exist"):
      it("returns ConsolidationGroupNotFound"):
        val missingId = GroupId()
        val service = buildService()
        assert(
          service.assign(missingId, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotFound(missingId)
        )

    describe("when consolidation group is not ReadyForWorkstation"):
      it("rejects Created"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = createdConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
        )

      it("rejects Picked"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = pickedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
        )

      it("rejects Assigned"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = assignedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
        )

      it("rejects Completed"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = completedConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
        )

      it("rejects Cancelled"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = cancelledConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
        )

    describe("when no idle workstation is available"):
      it("returns NoWorkstationAvailable"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.NoWorkstationAvailable(consolidationGroup.id)
        )

    describe("when workstation has insufficient slots"):
      it("returns NoWorkstationAvailable"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val threeOrderIds = List(OrderId(), OrderId(), OrderId())
        val consolidationGroup =
          readyForWorkstationConsolidationGroup(orderIds = threeOrderIds)
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation(slotCount = 2)
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.NoWorkstationAvailable(consolidationGroup.id)
        )

    describe("assigning"):
      it("assigns consolidation group to workstation"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        val result = service.assign(consolidationGroup.id, at).value
        assert(result.consolidationGroup.id == consolidationGroup.id)
        assert(result.consolidationGroup.workstationId == workstation.id)
        assert(result.workstation.id == workstation.id)
        assert(result.workstation.groupId == consolidationGroup.id)

      it("persists Assigned consolidation group"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        service.assign(consolidationGroup.id, at)
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Assigned]
        )

      it("persists Active workstation"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        service.assign(consolidationGroup.id, at)
        assert(
          workstationRepository.store(workstation.id).isInstanceOf[Workstation.Active]
        )

      it("events carry cross-aggregate references"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val workstationRepository = InMemoryWorkstationRepository()
        val consolidationGroup = readyForWorkstationConsolidationGroup()
        consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
        val workstation = idleWorkstation()
        workstationRepository.store(workstation.id) = workstation
        val service = buildService(
          consolidationGroupRepository = consolidationGroupRepository,
          workstationRepository = workstationRepository
        )
        val result = service.assign(consolidationGroup.id, at).value
        assert(result.consolidationGroupEvent.workstationId == workstation.id)
        assert(result.workstationEvent.groupId == consolidationGroup.id)
        assert(result.consolidationGroupEvent.occurredAt == at)
        assert(result.workstationEvent.occurredAt == at)
