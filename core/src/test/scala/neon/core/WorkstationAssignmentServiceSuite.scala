package neon.core

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationRepository, WorkstationType}
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class WorkstationAssignmentServiceSuite extends AnyFunSpec with OptionValues with EitherValues:
  val waveId = WaveId()
  val orderId = OrderId()
  val consolidationGroupId = ConsolidationGroupId()
  val workstationId = WorkstationId()
  val at = Instant.now()

  def readyForWorkstationConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.ReadyForWorkstation =
    ConsolidationGroup.ReadyForWorkstation(id, waveId, orderIds)

  def createdConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Created =
    ConsolidationGroup.Created(id, waveId, List(orderId))

  def pickedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(id, waveId, List(orderId))

  def assignedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Assigned =
    ConsolidationGroup.Assigned(id, waveId, List(orderId), workstationId)

  def completedConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Completed =
    ConsolidationGroup.Completed(id, waveId, List(orderId), workstationId)

  def cancelledConsolidationGroup(
      id: ConsolidationGroupId = consolidationGroupId
  ): ConsolidationGroup.Cancelled =
    ConsolidationGroup.Cancelled(id, waveId, List(orderId))

  def idleWorkstation(
      id: WorkstationId = workstationId,
      slotCount: Int = 5
  ): Workstation.Idle =
    Workstation.Idle(id, WorkstationType.PutWall, slotCount)

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

  private def setupReadyGroup(
      orderIds: List[OrderId] = List(orderId),
      workstation: Option[Workstation] = Some(idleWorkstation())
  ): (
      InMemoryConsolidationGroupRepository,
      InMemoryWorkstationRepository,
      ConsolidationGroup.ReadyForWorkstation,
      WorkstationAssignmentService
  ) =
    val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
    val workstationRepository = InMemoryWorkstationRepository()
    val consolidationGroup = readyForWorkstationConsolidationGroup(orderIds = orderIds)
    consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
    workstation.foreach(ws => workstationRepository.store(ws.id) = ws)
    val service = buildService(
      consolidationGroupRepository = consolidationGroupRepository,
      workstationRepository = workstationRepository
    )
    (consolidationGroupRepository, workstationRepository, consolidationGroup, service)

  describe("WorkstationAssignmentService"):
    describe("when consolidation group does not exist"):
      it("returns ConsolidationGroupNotFound"):
        val missingId = ConsolidationGroupId()
        val service = buildService()
        assert(
          service.assign(missingId, at).left.value ==
            WorkstationAssignmentError.ConsolidationGroupNotFound(missingId)
        )

    describe("when consolidation group is not ReadyForWorkstation"):
      val nonReadyStates = List(
        "Created" -> (() => createdConsolidationGroup()),
        "Picked" -> (() => pickedConsolidationGroup()),
        "Assigned" -> (() => assignedConsolidationGroup()),
        "Completed" -> (() => completedConsolidationGroup()),
        "Cancelled" -> (() => cancelledConsolidationGroup())
      )

      nonReadyStates.foreach { case (stateName, stateFactory) =>
        it(s"rejects $stateName with ConsolidationGroupNotReady"):
          val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
          val consolidationGroup = stateFactory()
          consolidationGroupRepository.store(consolidationGroup.id) = consolidationGroup
          val service =
            buildService(consolidationGroupRepository = consolidationGroupRepository)
          assert(
            service.assign(consolidationGroup.id, at).left.value ==
              WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroup.id)
          )
      }

    describe("when no idle workstation is available"):
      it("returns NoWorkstationAvailable and leaves state untouched"):
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupReadyGroup(workstation = None)

        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.NoWorkstationAvailable(consolidationGroup.id)
        )
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.ReadyForWorkstation]
        )
        assert(consolidationGroupRepository.events.isEmpty)
        assert(workstationRepository.events.isEmpty)

    describe("when workstation has insufficient slots"):
      it("returns NoWorkstationAvailable and does not persist partial writes"):
        val threeOrderIds = List(OrderId(), OrderId(), OrderId())
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupReadyGroup(
          orderIds = threeOrderIds,
          workstation = Some(idleWorkstation(slotCount = 2))
        )

        assert(
          service.assign(consolidationGroup.id, at).left.value ==
            WorkstationAssignmentError.NoWorkstationAvailable(consolidationGroup.id)
        )
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.ReadyForWorkstation]
        )
        assert(workstationRepository.store(workstationId).isInstanceOf[Workstation.Idle])
        assert(consolidationGroupRepository.events.isEmpty)
        assert(workstationRepository.events.isEmpty)

    describe("assigning"):
      it("assigns consolidation group to workstation and persists both transitions"):
        val (
          consolidationGroupRepository,
          workstationRepository,
          consolidationGroup,
          service
        ) = setupReadyGroup()

        val result = service.assign(consolidationGroup.id, at).value
        assert(result.consolidationGroup.id == consolidationGroup.id)
        assert(result.consolidationGroup.workstationId == workstationId)
        assert(result.workstation.id == workstationId)
        assert(result.workstation.consolidationGroupId == consolidationGroup.id)
        assert(result.consolidationGroupEvent.workstationId == workstationId)
        assert(result.workstationEvent.consolidationGroupId == consolidationGroup.id)
        assert(result.consolidationGroupEvent.occurredAt == at)
        assert(result.workstationEvent.occurredAt == at)
        assert(
          consolidationGroupRepository
            .store(consolidationGroup.id)
            .isInstanceOf[ConsolidationGroup.Assigned]
        )
        assert(workstationRepository.store(workstationId).isInstanceOf[Workstation.Active])
        assert(consolidationGroupRepository.events.size == 1)
        assert(workstationRepository.events.size == 1)
