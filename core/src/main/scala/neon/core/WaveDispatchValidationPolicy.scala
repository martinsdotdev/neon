package neon.core

import neon.carrier.Carrier
import neon.common.{CarrierId, LocationId}
import neon.location.{Location, LocationType}
import neon.order.Order

/** Pure validation rules for carrier/dock checks before wave release.
  */
object WaveDispatchValidationPolicy:

  /** Validates orders and dock assignments against carrier, location, and active-wave constraints.
    */
  def apply(
      orders: List[Order],
      dockAssignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules,
      carriersById: Map[CarrierId, Carrier],
      docksById: Map[LocationId, Location],
      activeAssignmentsByDock: Map[LocationId, List[ActiveDockCarrierAssignment]]
  ): Either[WavePlanningError, Unit] =
    if orders.isEmpty then Left(WavePlanningError.EmptyOrders)
    else if dockAssignments.isEmpty then Left(WavePlanningError.NoDockAssignments)
    else
      val orderCarrierIdsEither = resolveOrderCarrierIds(orders)
      orderCarrierIdsEither.flatMap: orderCarrierIds =>
        checkDuplicates(dockAssignments, rules)
          .flatMap(_ => validateCarriers(orderCarrierIds, dockAssignments, carriersById))
          .flatMap(_ => validateDocks(dockAssignments, docksById))
          .flatMap(_ => validateOrderCarrierCoverage(orderCarrierIds, dockAssignments))
          .flatMap(_ => validateActiveDockConflicts(dockAssignments, rules, activeAssignmentsByDock))

  private def resolveOrderCarrierIds(
      orders: List[Order]
  ): Either[WavePlanningError, List[CarrierId]] =
    orders.foldLeft[Either[WavePlanningError, List[CarrierId]]](Right(List.empty)):
      case (Left(err), _) => Left(err)
      case (Right(ids), order) =>
        order.carrierId match
          case Some(carrierId) => Right(ids :+ carrierId)
          case None            => Left(WavePlanningError.OrderWithoutCarrier(order.id))

  private def checkDuplicates(
      dockAssignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules
  ): Either[WavePlanningError, Unit] =
    firstDuplicate(dockAssignments.map(_.dockId))
      .map(WavePlanningError.DuplicateDockInAssignments.apply)
      .map(Left(_))
      .getOrElse:
        if rules.allowCarrierMultipleDocksWithinWave then Right(())
        else
          firstDuplicate(dockAssignments.map(_.carrierId))
            .map(WavePlanningError.DuplicateCarrierInAssignments.apply)
            .map(Left(_))
            .getOrElse(Right(()))

  private def validateCarriers(
      orderCarrierIds: List[CarrierId],
      dockAssignments: List[DockCarrierAssignment],
      carriersById: Map[CarrierId, Carrier]
  ): Either[WavePlanningError, Unit] =
    val carrierIds = (orderCarrierIds ++ dockAssignments.map(_.carrierId)).distinct
    carrierIds
      .collectFirst:
        case carrierId if !carriersById.contains(carrierId) =>
          WavePlanningError.CarrierNotFound(carrierId)
        case carrierId if !carriersById(carrierId).active =>
          WavePlanningError.CarrierInactive(carrierId)
      .map(Left(_))
      .getOrElse(Right(()))

  private def validateDocks(
      dockAssignments: List[DockCarrierAssignment],
      docksById: Map[LocationId, Location]
  ): Either[WavePlanningError, Unit] =
    dockAssignments
      .collectFirst:
        case assignment if !docksById.contains(assignment.dockId) =>
          WavePlanningError.DockNotFound(assignment.dockId)
        case assignment if docksById(assignment.dockId).locationType != LocationType.Dock =>
          WavePlanningError.DockIsNotShippingDock(assignment.dockId)
      .map(Left(_))
      .getOrElse(Right(()))

  private def validateOrderCarrierCoverage(
      orderCarrierIds: List[CarrierId],
      dockAssignments: List[DockCarrierAssignment]
  ): Either[WavePlanningError, Unit] =
    val assignedCarrierIds = dockAssignments.map(_.carrierId).toSet
    orderCarrierIds.distinct
      .find(carrierId => !assignedCarrierIds.contains(carrierId))
      .map(carrierId => Left(WavePlanningError.CarrierNotMappedToAnyDock(carrierId)))
      .getOrElse(Right(()))

  private def validateActiveDockConflicts(
      dockAssignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules,
      activeAssignmentsByDock: Map[LocationId, List[ActiveDockCarrierAssignment]]
  ): Either[WavePlanningError, Unit] =
    if !rules.enforceDockCarrierExclusivityAcrossActiveWaves then Right(())
    else
      dockAssignments
        .collectFirst(Function.unlift { assignment =>
          activeAssignmentsByDock
            .getOrElse(assignment.dockId, Nil)
            .find(_.carrierId != assignment.carrierId)
            .map(active =>
              WavePlanningError.DockConflict(
                dockId = assignment.dockId,
                requestedCarrierId = assignment.carrierId,
                activeCarrierId = active.carrierId,
                activeWaveId = active.waveId
              )
            )
        })
        .map(Left(_))
        .getOrElse(Right(()))

  private def firstDuplicate[A](values: List[A]): Option[A] =
    values
      .foldLeft((Set.empty[A], Option.empty[A])):
        case ((seen, duplicate @ Some(_)), _) => (seen, duplicate)
        case ((seen, None), value) =>
          if seen.contains(value) then (seen, Some(value)) else (seen + value, None)
      ._2
