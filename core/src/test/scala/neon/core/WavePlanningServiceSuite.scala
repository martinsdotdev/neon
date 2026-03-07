package neon.core

import neon.carrier.{Carrier, CarrierRepository}
import neon.common.{
  CarrierId,
  ConsolidationGroupId,
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  Priority,
  SkuId,
  TaskId,
  UomHierarchy,
  WaveId
}
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.location.{Location, LocationRepository, LocationType}
import neon.order.{Order, OrderLine}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.wave.{OrderGrouping, UomExpansion, Wave, WaveEvent, WavePlanner, WaveRepository}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class WavePlanningServiceSuite extends AnyFunSpec:
  private val at = Instant.now()
  private val skuId = SkuId()

  private val carrierA = Carrier(CarrierId(), "CARRIER-A", "Carrier A", active = true)
  private val carrierB = Carrier(CarrierId(), "CARRIER-B", "Carrier B", active = true)
  private val dockA = Location(LocationId(), "DOCK-A", None, LocationType.Dock)

  private def order(
      carrierId: Option[CarrierId],
      quantity: Int = 10,
      packagingLevel: PackagingLevel = PackagingLevel.Each,
      sku: SkuId = skuId
  ): Order =
    Order(
      id = OrderId(),
      priority = Priority.Normal,
      lines = List(OrderLine(sku, packagingLevel, quantity)),
      carrierId = carrierId
    )

  class InMemoryCarrierRepository(initial: Map[CarrierId, Carrier]) extends CarrierRepository:
    def findById(id: CarrierId): Option[Carrier] = initial.get(id)

  class InMemoryLocationRepository(initial: Map[LocationId, Location]) extends LocationRepository:
    def findById(id: LocationId): Option[Location] = initial.get(id)

  class RecordingWaveDispatchAssignmentRepository extends WaveDispatchAssignmentRepository:
    var activeAssignmentsByDock: Map[LocationId, List[ActiveDockCarrierAssignment]] = Map.empty
    var reserveResult: Either[WavePlanningError.DockConflict, Unit] = Right(())
    val reserveCalls
        : mutable.ListBuffer[(WaveId, List[DockCarrierAssignment], WaveDispatchRules)] =
      mutable.ListBuffer.empty

    def findActiveByDock(dockId: LocationId): List[ActiveDockCarrierAssignment] =
      activeAssignmentsByDock.getOrElse(dockId, Nil)

    def reserveForWave(
        waveId: WaveId,
        assignments: List[DockCarrierAssignment],
        rules: WaveDispatchRules
    ): Either[WavePlanningError.DockConflict, Unit] =
      reserveCalls += ((waveId, assignments, rules))
      reserveResult

  class FixedWaveDispatchRulesProvider(rules: WaveDispatchRules) extends WaveDispatchRulesProvider:
    def current(): WaveDispatchRules = rules

  class InMemoryWaveRepository extends WaveRepository:
    val store: mutable.Map[WaveId, Wave] = mutable.Map.empty
    val events: mutable.ListBuffer[WaveEvent] = mutable.ListBuffer.empty
    def findById(id: WaveId): Option[Wave] = store.get(id)
    def save(wave: Wave, event: WaveEvent): Unit =
      store(wave.id) = wave
      events += event

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
      entries.foreach((task, event) => save(task, event))

  class InMemoryConsolidationGroupRepository extends ConsolidationGroupRepository:
    val store: mutable.Map[ConsolidationGroupId, ConsolidationGroup] = mutable.Map.empty
    def findById(id: ConsolidationGroupId): Option[ConsolidationGroup] = store.get(id)
    def findByWaveId(waveId: WaveId): List[ConsolidationGroup] =
      store.values.filter(_.waveId == waveId).toList
    def save(consolidationGroup: ConsolidationGroup, event: ConsolidationGroupEvent): Unit =
      store(consolidationGroup.id) = consolidationGroup
    def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit =
      entries.foreach((consolidationGroup, event) => save(consolidationGroup, event))

  private def buildServices(
      carrierStore: Map[CarrierId, Carrier],
      locationStore: Map[LocationId, Location],
      rules: WaveDispatchRules = WaveDispatchRules(),
      dispatchRepository: RecordingWaveDispatchAssignmentRepository =
        RecordingWaveDispatchAssignmentRepository()
  ): (WavePlanningService, RecordingWaveDispatchAssignmentRepository, InMemoryWaveRepository) =
    val waveRepository = InMemoryWaveRepository()
    val taskRepository = InMemoryTaskRepository()
    val consolidationRepository = InMemoryConsolidationGroupRepository()
    val waveReleaseService =
      WaveReleaseService(waveRepository, taskRepository, consolidationRepository)
    val service = WavePlanningService(
      carrierRepository = InMemoryCarrierRepository(carrierStore),
      locationRepository = InMemoryLocationRepository(locationStore),
      waveDispatchAssignmentRepository = dispatchRepository,
      waveDispatchRulesProvider = FixedWaveDispatchRulesProvider(rules),
      waveReleaseService = waveReleaseService
    )
    (service, dispatchRepository, waveRepository)

  describe("WavePlanningService"):
    it("plans, reserves, and releases a wave on the happy path"):
      val (service, dispatchRepository, waveRepository) = buildServices(
        carrierStore = Map(carrierA.id -> carrierA),
        locationStore = Map(dockA.id -> dockA)
      )

      val orders = List(order(Some(carrierA.id)))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))

      val result = service.planAndRelease(orders, OrderGrouping.Single, assignments, at)

      assert(result.isRight)
      val planningResult = result.toOption.get
      assert(planningResult.release.tasks.size == 1)
      assert(planningResult.release.wave.id == planningResult.wavePlan.wave.id)
      assert(dispatchRepository.reserveCalls.size == 1)
      assert(dispatchRepository.reserveCalls.head._2 == assignments)
      assert(waveRepository.events.size == 1)

    it("returns validation error and does not reserve when order has no carrier"):
      val (service, dispatchRepository, waveRepository) = buildServices(
        carrierStore = Map(carrierA.id -> carrierA),
        locationStore = Map(dockA.id -> dockA)
      )
      val orderWithoutCarrier = order(carrierId = None)
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))

      val result =
        service.planAndRelease(List(orderWithoutCarrier), OrderGrouping.Single, assignments, at)

      assert(result == Left(WavePlanningError.OrderWithoutCarrier(orderWithoutCarrier.id)))
      assert(dispatchRepository.reserveCalls.isEmpty)
      assert(waveRepository.events.isEmpty)

    it("returns dock conflict from reserveForWave and does not release wave"):
      val dispatchRepository = RecordingWaveDispatchAssignmentRepository()
      val activeWaveId = WaveId()
      dispatchRepository.reserveResult = Left(
        WavePlanningError.DockConflict(
          dockId = dockA.id,
          requestedCarrierId = carrierA.id,
          activeCarrierId = carrierB.id,
          activeWaveId = activeWaveId
        )
      )

      val (service, _, waveRepository) = buildServices(
        carrierStore = Map(carrierA.id -> carrierA, carrierB.id -> carrierB),
        locationStore = Map(dockA.id -> dockA),
        dispatchRepository = dispatchRepository
      )

      val orders = List(order(Some(carrierA.id)))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = service.planAndRelease(orders, OrderGrouping.Single, assignments, at)

      assert(result == dispatchRepository.reserveResult)
      assert(dispatchRepository.reserveCalls.size == 1)
      assert(waveRepository.events.isEmpty)

    it("supports custom lineResolution integration with UomExpansion"):
      val skuForExpansion = SkuId()
      val orderForExpansion =
        order(
          carrierId = Some(carrierA.id),
          quantity = 28,
          packagingLevel = PackagingLevel.Each,
          sku = skuForExpansion
        )
      val hierarchies =
        Map(skuForExpansion -> UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6))

      val (service, _, _) = buildServices(
        carrierStore = Map(carrierA.id -> carrierA),
        locationStore = Map(dockA.id -> dockA)
      )

      val result = service.planAndRelease(
        orders = List(orderForExpansion),
        grouping = OrderGrouping.Single,
        dockAssignments = List(DockCarrierAssignment(dockA.id, carrierA.id)),
        at = at,
        lineResolution = (waveId, orderId, line) =>
          UomExpansion(waveId, orderId, line, hierarchies.getOrElse(line.skuId, UomHierarchy.empty))
      )

      assert(result.isRight)
      val tasks = result.toOption.get.release.tasks.map(_._1)
      assert(tasks.size == 3)
      assert(tasks.exists(t => t.packagingLevel == PackagingLevel.Pallet && t.requestedQuantity == 1))
      assert(tasks.exists(t => t.packagingLevel == PackagingLevel.Case && t.requestedQuantity == 1))
      assert(tasks.exists(t => t.packagingLevel == PackagingLevel.Each && t.requestedQuantity == 2))

    it("keeps Order compatibility outside WavePlanningService when carrierId is absent"):
      val compatibleOrder =
        Order(OrderId(), Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, 3)))
      val wavePlan = WavePlanner.plan(List(compatibleOrder), OrderGrouping.Single, at)
      assert(wavePlan.taskRequests.nonEmpty)
