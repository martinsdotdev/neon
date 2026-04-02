package neon.core

import neon.carrier.Carrier
import neon.common.{CarrierId, LocationId, OrderId, PackagingLevel, Priority, SkuId, WaveId}
import neon.location.{Location, LocationType}
import neon.order.{Order, OrderLine}
import org.scalatest.funspec.AnyFunSpec

class WaveDispatchValidationPolicySuite extends AnyFunSpec:
  private val skuId = SkuId()

  private val carrierA = Carrier(CarrierId(), "CARRIER-A", "Carrier A", active = true)
  private val carrierB = Carrier(CarrierId(), "CARRIER-B", "Carrier B", active = true)
  private val inactiveCarrier = Carrier(CarrierId(), "CARRIER-X", "Carrier X", active = false)

  private val dockA = Location(LocationId(), "DOCK-A", None, LocationType.Dock)
  private val dockB = Location(LocationId(), "DOCK-B", None, LocationType.Dock)
  private val pickFace = Location(LocationId(), "PICK-01", None, LocationType.Pick)

  private def orderWithCarrier(
      carrierId: CarrierId,
      id: OrderId = OrderId()
  ): Order =
    Order(
      id = id,
      priority = Priority.Normal,
      lines = List(OrderLine(skuId, PackagingLevel.Each, 1)),
      carrierId = Some(carrierId)
    )

  private def orderWithoutCarrier(id: OrderId = OrderId()): Order =
    Order(
      id = id,
      priority = Priority.Normal,
      lines = List(OrderLine(skuId, PackagingLevel.Each, 1)),
      carrierId = None
    )

  private val defaultRules = WaveDispatchRules()

  describe("WaveDispatchValidationPolicy"):
    it("accepts valid orders, carriers, docks, and assignments"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Right(()))

    it("fails when an order has no carrier"):
      val order = orderWithoutCarrier()
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders = List(order),
        dockAssignments = assignments,
        rules = defaultRules,
        carriersById = Map(carrierA.id -> carrierA),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.OrderWithoutCarrier(order.id)))

    it("fails when a referenced carrier is inactive"):
      val orders = List(orderWithCarrier(inactiveCarrier.id))
      val assignments = List(DockCarrierAssignment(dockA.id, inactiveCarrier.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(inactiveCarrier.id -> inactiveCarrier),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.CarrierInactive(inactiveCarrier.id)))

    it("fails when a mapped location is not a shipping dock"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments = List(DockCarrierAssignment(pickFace.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA),
        docksById = Map(pickFace.id -> pickFace),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.DockIsNotShippingDock(pickFace.id)))

    it("fails when the same carrier is mapped to multiple docks in the same wave"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments =
        List(
          DockCarrierAssignment(dockA.id, carrierA.id),
          DockCarrierAssignment(dockB.id, carrierA.id)
        )
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA),
        docksById = Map(dockA.id -> dockA, dockB.id -> dockB),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.DuplicateCarrierInAssignments(carrierA.id)))

    it("fails when the same dock appears more than once in the same wave mapping"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments =
        List(
          DockCarrierAssignment(dockA.id, carrierA.id),
          DockCarrierAssignment(dockA.id, carrierB.id)
        )
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA, carrierB.id -> carrierB),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.DuplicateDockInAssignments(dockA.id)))

    it("fails when an order carrier is not mapped to any dock"):
      val orders = List(orderWithCarrier(carrierA.id), orderWithCarrier(carrierB.id))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA, carrierB.id -> carrierB),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock = Map.empty
      )
      assert(result == Left(WavePlanningError.CarrierNotMappedToAnyDock(carrierB.id)))

    it(
      "fails with dock conflict when another active wave uses the same dock with different carrier"
    ):
      val activeWaveId = WaveId()
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA, carrierB.id -> carrierB),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock =
          Map(dockA.id -> List(ActiveDockCarrierAssignment(activeWaveId, dockA.id, carrierB.id)))
      )
      assert(
        result == Left(
          WavePlanningError.DockConflict(
            dockId = dockA.id,
            requestedCarrierId = carrierA.id,
            activeCarrierId = carrierB.id,
            activeWaveId = activeWaveId
          )
        )
      )

    it("accepts same dock when active wave uses the same carrier"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        defaultRules,
        carriersById = Map(carrierA.id -> carrierA),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock =
          Map(dockA.id -> List(ActiveDockCarrierAssignment(WaveId(), dockA.id, carrierA.id)))
      )
      assert(result == Right(()))

    it("accepts different carrier on same dock when cross-wave exclusivity rule is disabled"):
      val orders = List(orderWithCarrier(carrierA.id))
      val assignments = List(DockCarrierAssignment(dockA.id, carrierA.id))
      val relaxedRules = defaultRules.copy(enforceDockCarrierExclusivityAcrossActiveWaves = false)
      val result = WaveDispatchValidationPolicy(
        orders,
        assignments,
        relaxedRules,
        carriersById = Map(carrierA.id -> carrierA, carrierB.id -> carrierB),
        docksById = Map(dockA.id -> dockA),
        activeAssignmentsByDock =
          Map(dockA.id -> List(ActiveDockCarrierAssignment(WaveId(), dockA.id, carrierB.id)))
      )
      assert(result == Right(()))
