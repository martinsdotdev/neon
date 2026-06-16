package neon.app.http

import neon.core.{
  ConsolidationGroupAdvanceError,
  ConsolidationGroupCancellationError,
  ConsolidationGroupCompletionError,
  CycleCountError,
  HandlingUnitLifecycleError,
  InboundDeliveryError,
  InventoryError,
  SlotError,
  StockPositionError,
  TaskCompletionError,
  TaskLifecycleError,
  TransportOrderCancellationError,
  TransportOrderConfirmationError,
  WaveCancellationError,
  WavePlanningError,
  WorkstationAssignmentError,
  WorkstationLifecycleError
}
import org.apache.pekko.http.scaladsl.model.{StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route

/** Maps a domain error to its RFC 9457 problem response. One given instance per error ADT keeps the
  * error-to-status knowledge at one seam; routes call [[ProblemMapper.completeProblem]] instead of
  * hand-rolling matches. See ADR 0011.
  */
trait ProblemMapper[E]:
  def toProblem(error: E): ProblemDetails

object ProblemMapper:

  /** Completes the request with the error's problem response. */
  def completeProblem[E](error: E)(using mapper: ProblemMapper[E]): Route =
    val problem = mapper.toProblem(error)
    complete(StatusCode.int2StatusCode(problem.status) -> problem)

  given ProblemMapper[TaskCompletionError] with
    def toProblem(error: TaskCompletionError): ProblemDetails = error match
      case TaskCompletionError.TaskNotFound(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "task-not-found",
          title = "Task not found",
          detail = Some(s"Task ${taskId.value} was not found")
        )
      case TaskCompletionError.TaskNotAssigned(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "task-not-assigned",
          title = "Task not assigned",
          detail = Some(
            s"Task ${taskId.value} is not in the Assigned state required for completion"
          )
        )
      case TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "invalid-actual-quantity",
          title = "Invalid actual quantity",
          detail = Some(s"Actual quantity $actualQuantity for task ${taskId.value} is invalid")
        )
      case TaskCompletionError.VerificationRequired(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.PreconditionRequired,
          slug = "verification-required",
          title = "Verification required",
          detail = Some(s"Task ${taskId.value} requires verification before completion")
        )

  given ProblemMapper[TaskLifecycleError] with
    def toProblem(error: TaskLifecycleError): ProblemDetails = error match
      case TaskLifecycleError.TaskNotFound(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "task-not-found",
          title = "Task not found",
          detail = Some(s"Task ${taskId.value} was not found")
        )
      case TaskLifecycleError.TaskInWrongState(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "task-in-wrong-state",
          title = "Task in wrong state",
          detail = Some(s"Task ${taskId.value} is in the wrong state for this transition")
        )
      case TaskLifecycleError.TaskAlreadyTerminal(taskId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "task-already-terminal",
          title = "Task already terminal",
          detail = Some(s"Task ${taskId.value} is already in a terminal state")
        )
      case TaskLifecycleError.UserNotFound(userId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "user-not-found",
          title = "User not found",
          detail = Some(s"User ${userId.value} was not found")
        )
      case TaskLifecycleError.UserNotActive(userId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "user-not-active",
          title = "User not active",
          detail = Some(s"User ${userId.value} is not active")
        )

  given ProblemMapper[WavePlanningError] with
    def toProblem(error: WavePlanningError): ProblemDetails = error match
      case WavePlanningError.EmptyOrders =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "empty-orders",
          title = "Empty orders",
          detail = Some("The wave planning request contained no orders")
        )
      case WavePlanningError.NoDockAssignments =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "no-dock-assignments",
          title = "No dock assignments",
          detail = Some("The wave planning request contained no dock assignments")
        )
      case WavePlanningError.OrderWithoutCarrier(orderId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "order-without-carrier",
          title = "Order without carrier",
          detail = Some(s"Order ${orderId.value} has no carrier")
        )
      case WavePlanningError.CarrierNotFound(carrierId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "carrier-not-found",
          title = "Carrier not found",
          detail = Some(s"Carrier ${carrierId.value} was not found")
        )
      case WavePlanningError.CarrierInactive(carrierId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "carrier-inactive",
          title = "Carrier inactive",
          detail = Some(s"Carrier ${carrierId.value} is inactive")
        )
      case WavePlanningError.DockNotFound(dockId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "dock-not-found",
          title = "Dock not found",
          detail = Some(s"Dock ${dockId.value} was not found")
        )
      case WavePlanningError.DockIsNotShippingDock(dockId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "dock-is-not-shipping-dock",
          title = "Dock is not a shipping dock",
          detail = Some(s"Dock ${dockId.value} is not a shipping dock")
        )
      case WavePlanningError.DuplicateCarrierInAssignments(carrierId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "duplicate-carrier-in-assignments",
          title = "Duplicate carrier in assignments",
          detail = Some(
            s"Carrier ${carrierId.value} appears more than once in the dock assignments"
          )
        )
      case WavePlanningError.DuplicateDockInAssignments(dockId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "duplicate-dock-in-assignments",
          title = "Duplicate dock in assignments",
          detail = Some(s"Dock ${dockId.value} appears more than once in the dock assignments")
        )
      case WavePlanningError.CarrierNotMappedToAnyDock(carrierId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "carrier-not-mapped-to-any-dock",
          title = "Carrier not mapped to any dock",
          detail = Some(s"Carrier ${carrierId.value} is not mapped to any dock")
        )
      case WavePlanningError.DockConflict(
            dockId,
            requestedCarrierId,
            activeCarrierId,
            activeWaveId
          ) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "dock-conflict",
          title = "Dock conflict",
          detail = Some(
            s"Dock ${dockId.value} requested for carrier ${requestedCarrierId.value} is held " +
              s"by carrier ${activeCarrierId.value} for wave ${activeWaveId.value}"
          )
        )

  given ProblemMapper[WaveCancellationError] with
    def toProblem(error: WaveCancellationError): ProblemDetails = error match
      case WaveCancellationError.WaveNotFound(waveId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "wave-not-found",
          title = "Wave not found",
          detail = Some(s"Wave ${waveId.value} was not found")
        )
      case WaveCancellationError.WaveAlreadyTerminal(waveId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "wave-already-terminal",
          title = "Wave already terminal",
          detail = Some(s"Wave ${waveId.value} is already in a terminal state")
        )

  given ProblemMapper[ConsolidationGroupCompletionError] with
    def toProblem(error: ConsolidationGroupCompletionError): ProblemDetails = error match
      case ConsolidationGroupCompletionError.ConsolidationGroupNotFound(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "consolidation-group-not-found",
          title = "Consolidation group not found",
          detail = Some(s"Consolidation group ${consolidationGroupId.value} was not found")
        )
      case ConsolidationGroupCompletionError.ConsolidationGroupNotAssigned(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "consolidation-group-not-assigned",
          title = "Consolidation group not assigned",
          detail = Some(
            s"Consolidation group ${consolidationGroupId.value} is not in the Assigned state"
          )
        )
      case ConsolidationGroupCompletionError.WorkstationNotFound(workstationId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "workstation-not-found",
          title = "Workstation not found",
          detail = Some(s"Workstation ${workstationId.value} was not found")
        )
      case ConsolidationGroupCompletionError.WorkstationNotActive(workstationId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "workstation-not-active",
          title = "Workstation not active",
          detail = Some(s"Workstation ${workstationId.value} is not in the Active state")
        )

  given ProblemMapper[ConsolidationGroupAdvanceError] with
    def toProblem(error: ConsolidationGroupAdvanceError): ProblemDetails = error match
      case ConsolidationGroupAdvanceError.ConsolidationGroupNotFound(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "consolidation-group-not-found",
          title = "Consolidation group not found",
          detail = Some(s"Consolidation group ${consolidationGroupId.value} was not found")
        )
      case ConsolidationGroupAdvanceError.ConsolidationGroupNotPicked(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "consolidation-group-not-picked",
          title = "Consolidation group not picked",
          detail = Some(
            s"Consolidation group ${consolidationGroupId.value} is not in the Picked state"
          )
        )

  given ProblemMapper[ConsolidationGroupCancellationError] with
    def toProblem(error: ConsolidationGroupCancellationError): ProblemDetails = error match
      case ConsolidationGroupCancellationError.ConsolidationGroupNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "consolidation-group-not-found",
          title = "Consolidation group not found",
          detail = Some(s"Consolidation group ${id.value} was not found")
        )
      case ConsolidationGroupCancellationError.ConsolidationGroupAlreadyTerminal(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "consolidation-group-already-terminal",
          title = "Consolidation group already terminal",
          detail = Some(s"Consolidation group ${id.value} is already in a terminal state")
        )

  given ProblemMapper[HandlingUnitLifecycleError] with
    def toProblem(error: HandlingUnitLifecycleError): ProblemDetails = error match
      case HandlingUnitLifecycleError.HandlingUnitNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "handling-unit-not-found",
          title = "Handling unit not found",
          detail = Some(s"Handling unit ${id.value} was not found")
        )
      case HandlingUnitLifecycleError.HandlingUnitInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "handling-unit-in-wrong-state",
          title = "Handling unit in wrong state",
          detail = Some(s"Handling unit ${id.value} is in the wrong state for this transition")
        )

  given ProblemMapper[SlotError] with
    def toProblem(error: SlotError): ProblemDetails = error match
      case SlotError.SlotNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "slot-not-found",
          title = "Slot not found",
          detail = Some(s"Slot ${id.value} was not found")
        )
      case SlotError.SlotInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "slot-in-wrong-state",
          title = "Slot in wrong state",
          detail = Some(s"Slot ${id.value} is in the wrong state for this transition")
        )

  given ProblemMapper[TransportOrderConfirmationError] with
    def toProblem(error: TransportOrderConfirmationError): ProblemDetails = error match
      case TransportOrderConfirmationError.TransportOrderNotFound(transportOrderId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "transport-order-not-found",
          title = "Transport order not found",
          detail = Some(s"Transport order ${transportOrderId.value} was not found")
        )
      case TransportOrderConfirmationError.TransportOrderNotPending(transportOrderId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "transport-order-not-pending",
          title = "Transport order not pending",
          detail = Some(s"Transport order ${transportOrderId.value} is not in the Pending state")
        )
      case TransportOrderConfirmationError.HandlingUnitNotFound(handlingUnitId) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "handling-unit-not-found",
          title = "Handling unit not found",
          detail = Some(s"Handling unit ${handlingUnitId.value} was not found")
        )
      case TransportOrderConfirmationError.HandlingUnitNotPickCreated(handlingUnitId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "handling-unit-not-pick-created",
          title = "Handling unit not pick created",
          detail = Some(
            s"Handling unit ${handlingUnitId.value} is not in the PickCreated state"
          )
        )

  given ProblemMapper[TransportOrderCancellationError] with
    def toProblem(error: TransportOrderCancellationError): ProblemDetails = error match
      case TransportOrderCancellationError.TransportOrderNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "transport-order-not-found",
          title = "Transport order not found",
          detail = Some(s"Transport order ${id.value} was not found")
        )
      case TransportOrderCancellationError.TransportOrderAlreadyTerminal(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "transport-order-already-terminal",
          title = "Transport order already terminal",
          detail = Some(s"Transport order ${id.value} is already in a terminal state")
        )

  given ProblemMapper[WorkstationAssignmentError] with
    def toProblem(error: WorkstationAssignmentError): ProblemDetails = error match
      case WorkstationAssignmentError.ConsolidationGroupNotFound(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "consolidation-group-not-found",
          title = "Consolidation group not found",
          detail = Some(s"Consolidation group ${consolidationGroupId.value} was not found")
        )
      case WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "consolidation-group-not-ready",
          title = "Consolidation group not ready",
          detail = Some(
            s"Consolidation group ${consolidationGroupId.value} is not in the " +
              "ReadyForWorkstation state"
          )
        )
      case WorkstationAssignmentError.NoWorkstationAvailable(consolidationGroupId) =>
        ProblemDetails.of(
          status = StatusCodes.ServiceUnavailable,
          slug = "no-workstation-available",
          title = "No workstation available",
          detail = Some(
            s"No idle put-wall workstation is available for consolidation group " +
              s"${consolidationGroupId.value}"
          )
        )

  given ProblemMapper[WorkstationLifecycleError] with
    def toProblem(error: WorkstationLifecycleError): ProblemDetails = error match
      case WorkstationLifecycleError.WorkstationNotFound(workstationId) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "workstation-not-found",
          title = "Workstation not found",
          detail = Some(s"Workstation ${workstationId.value} was not found")
        )
      case WorkstationLifecycleError.WorkstationInWrongState(workstationId) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "workstation-in-wrong-state",
          title = "Workstation in wrong state",
          detail = Some(
            s"Workstation ${workstationId.value} is in the wrong state for this transition"
          )
        )

  given ProblemMapper[InventoryError] with
    def toProblem(error: InventoryError): ProblemDetails = error match
      case InventoryError.InventoryNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "inventory-not-found",
          title = "Inventory not found",
          detail = Some(s"Inventory ${id.value} was not found")
        )
      case InventoryError.InsufficientAvailable(id, requested, available) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "insufficient-available",
          title = "Insufficient available quantity",
          detail = Some(
            s"Inventory ${id.value} has $available available but $requested was requested"
          )
        )
      case InventoryError.InsufficientReserved(id, requested, reserved) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "insufficient-reserved",
          title = "Insufficient reserved quantity",
          detail = Some(
            s"Inventory ${id.value} has $reserved reserved but $requested was requested"
          )
        )
      case InventoryError.InvalidQuantity(id) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "invalid-quantity",
          title = "Invalid quantity",
          detail = Some(s"The requested quantity for inventory ${id.value} is invalid")
        )
      case InventoryError.ReservedNotZero(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "reserved-not-zero",
          title = "Reserved quantity not zero",
          detail = Some(s"Inventory ${id.value} still has reserved quantity")
        )

  given ProblemMapper[StockPositionError] with
    def toProblem(error: StockPositionError): ProblemDetails = error match
      case StockPositionError.StockPositionNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "stock-position-not-found",
          title = "Stock position not found",
          detail = Some(s"Stock position ${id.value} was not found")
        )
      case StockPositionError.InvalidQuantity(id) =>
        ProblemDetails.of(
          status = StatusCodes.UnprocessableEntity,
          slug = "invalid-quantity",
          title = "Invalid quantity",
          detail = Some(s"The requested quantity for stock position ${id.value} is invalid")
        )
      case StockPositionError.InsufficientAvailable(id, requested, available) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "insufficient-available",
          title = "Insufficient available quantity",
          detail = Some(
            s"Stock position ${id.value} has $available available but $requested was requested"
          )
        )
      case StockPositionError.InsufficientBlocked(id, requested, blocked) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "insufficient-blocked",
          title = "Insufficient blocked quantity",
          detail = Some(
            s"Stock position ${id.value} has $blocked blocked but $requested was requested"
          )
        )

  given ProblemMapper[CycleCountError] with
    def toProblem(error: CycleCountError): ProblemDetails = error match
      case CycleCountError.CycleCountNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "cycle-count-not-found",
          title = "Cycle count not found",
          detail = Some(s"Cycle count ${id.value} was not found")
        )
      case CycleCountError.CycleCountInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "cycle-count-in-wrong-state",
          title = "Cycle count in wrong state",
          detail = Some(s"Cycle count ${id.value} is in the wrong state for this transition")
        )
      case CycleCountError.CountTaskNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "count-task-not-found",
          title = "Count task not found",
          detail = Some(s"Count task ${id.value} was not found")
        )
      case CycleCountError.CountTaskInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "count-task-in-wrong-state",
          title = "Count task in wrong state",
          detail = Some(s"Count task ${id.value} is in the wrong state for this transition")
        )

  given ProblemMapper[InboundDeliveryError] with
    def toProblem(error: InboundDeliveryError): ProblemDetails = error match
      case InboundDeliveryError.DeliveryNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "delivery-not-found",
          title = "Delivery not found",
          detail = Some(s"Inbound delivery ${id.value} was not found")
        )
      case InboundDeliveryError.DeliveryInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "delivery-in-wrong-state",
          title = "Delivery in wrong state",
          detail = Some(s"Inbound delivery ${id.value} is in the wrong state for this transition")
        )
      case InboundDeliveryError.ReceiptNotFound(id) =>
        ProblemDetails.of(
          status = StatusCodes.NotFound,
          slug = "receipt-not-found",
          title = "Receipt not found",
          detail = Some(s"Goods receipt ${id.value} was not found")
        )
      case InboundDeliveryError.ReceiptInWrongState(id) =>
        ProblemDetails.of(
          status = StatusCodes.Conflict,
          slug = "receipt-in-wrong-state",
          title = "Receipt in wrong state",
          detail = Some(s"Goods receipt ${id.value} is in the wrong state for this transition")
        )
