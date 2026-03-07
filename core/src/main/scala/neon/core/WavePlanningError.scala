package neon.core

import neon.common.{CarrierId, LocationId, OrderId, WaveId}

/** Errors that can occur during wave planning and release orchestration.
  */
sealed trait WavePlanningError

object WavePlanningError:
  case object EmptyOrders extends WavePlanningError
  case object NoDockAssignments extends WavePlanningError
  case class OrderWithoutCarrier(orderId: OrderId) extends WavePlanningError
  case class CarrierNotFound(carrierId: CarrierId) extends WavePlanningError
  case class CarrierInactive(carrierId: CarrierId) extends WavePlanningError
  case class DockNotFound(dockId: LocationId) extends WavePlanningError
  case class DockIsNotShippingDock(dockId: LocationId) extends WavePlanningError
  case class DuplicateCarrierInAssignments(carrierId: CarrierId) extends WavePlanningError
  case class DuplicateDockInAssignments(dockId: LocationId) extends WavePlanningError
  case class CarrierNotMappedToAnyDock(carrierId: CarrierId) extends WavePlanningError
  case class DockConflict(
      dockId: LocationId,
      requestedCarrierId: CarrierId,
      activeCarrierId: CarrierId,
      activeWaveId: WaveId
  ) extends WavePlanningError
