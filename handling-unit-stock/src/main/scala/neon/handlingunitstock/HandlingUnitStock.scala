package neon.handlingunit

import neon.common.{
  AdjustmentReasonCode,
  ContainerId,
  HandlingUnitStockId,
  InventoryStatus,
  SlotCode,
  StockLockType,
  StockPositionId
}

import java.time.Instant

/** Container-level inventory position with 4-bucket quantity model. Keyed by (container, slot,
  * stock position). Tracks on-hand stock decomposed into available, allocated (outbound), reserved
  * (internal), and blocked (administrative hold) buckets.
  *
  * Invariant: `onHandQuantity == availableQuantity + allocatedQuantity + reservedQuantity +
  * blockedQuantity`, with all quantities non-negative.
  */
case class HandlingUnitStock private[handlingunitstock] (
    id: HandlingUnitStockId,
    containerId: ContainerId,
    slotCode: SlotCode,
    stockPositionId: StockPositionId,
    physicalContainer: Boolean,
    status: InventoryStatus,
    onHandQuantity: Int,
    availableQuantity: Int,
    allocatedQuantity: Int,
    reservedQuantity: Int,
    blockedQuantity: Int
):

  private def validateInvariant(): Unit =
    require(onHandQuantity >= 0, s"onHandQuantity must be non-negative, got $onHandQuantity")
    require(
      availableQuantity >= 0,
      s"availableQuantity must be non-negative, got $availableQuantity"
    )
    require(
      allocatedQuantity >= 0,
      s"allocatedQuantity must be non-negative, got $allocatedQuantity"
    )
    require(reservedQuantity >= 0, s"reservedQuantity must be non-negative, got $reservedQuantity")
    require(blockedQuantity >= 0, s"blockedQuantity must be non-negative, got $blockedQuantity")
    require(
      onHandQuantity == availableQuantity + allocatedQuantity + reservedQuantity + blockedQuantity,
      s"invariant violated: onHand $onHandQuantity != available $availableQuantity + allocated $allocatedQuantity + reserved $reservedQuantity + blocked $blockedQuantity"
    )

  /** Allocates quantity from available for outbound orders. */
  def allocate(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Allocated) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(
      quantity <= availableQuantity,
      s"quantity $quantity exceeds available $availableQuantity"
    )
    val updated = copy(
      availableQuantity = availableQuantity - quantity,
      allocatedQuantity = allocatedQuantity + quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Allocated(id, quantity, at)
    (updated, event)

  /** Releases allocated quantity back to available (task cancellation). */
  def deallocate(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Deallocated) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(
      quantity <= allocatedQuantity,
      s"quantity $quantity exceeds allocated $allocatedQuantity"
    )
    val updated = copy(
      availableQuantity = availableQuantity + quantity,
      allocatedQuantity = allocatedQuantity - quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Deallocated(id, quantity, at)
    (updated, event)

  /** Adds quantity from inbound receiving. Increases both onHand and available. */
  def addQuantity(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.QuantityAdded) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    val updated = copy(
      onHandQuantity = onHandQuantity + quantity,
      availableQuantity = availableQuantity + quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.QuantityAdded(id, quantity, at)
    (updated, event)

  /** Consumes allocated quantity on task completion. Decreases both onHand and allocated. */
  def consumeAllocated(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.AllocatedConsumed) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(
      quantity <= allocatedQuantity,
      s"quantity $quantity exceeds allocated $allocatedQuantity"
    )
    val updated = copy(
      onHandQuantity = onHandQuantity - quantity,
      allocatedQuantity = allocatedQuantity - quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.AllocatedConsumed(id, quantity, at)
    (updated, event)

  /** Reserves quantity from available for internal operations (counting, relocation). */
  def reserve(
      quantity: Int,
      lockType: StockLockType,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Reserved) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(
      quantity <= availableQuantity,
      s"quantity $quantity exceeds available $availableQuantity"
    )
    val updated = copy(
      availableQuantity = availableQuantity - quantity,
      reservedQuantity = reservedQuantity + quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Reserved(id, quantity, lockType, at)
    (updated, event)

  /** Releases reserved quantity back to available. */
  def releaseReservation(
      quantity: Int,
      lockType: StockLockType,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.ReservationReleased) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(quantity <= reservedQuantity, s"quantity $quantity exceeds reserved $reservedQuantity")
    val updated = copy(
      availableQuantity = availableQuantity + quantity,
      reservedQuantity = reservedQuantity - quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.ReservationReleased(id, quantity, lockType, at)
    (updated, event)

  /** Blocks quantity from available (administrative hold, recall). */
  def block(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Blocked) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(
      quantity <= availableQuantity,
      s"quantity $quantity exceeds available $availableQuantity"
    )
    val updated = copy(
      availableQuantity = availableQuantity - quantity,
      blockedQuantity = blockedQuantity + quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Blocked(id, quantity, at)
    (updated, event)

  /** Unblocks quantity back to available. */
  def unblock(
      quantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Unblocked) =
    require(quantity > 0, s"quantity must be positive, got $quantity")
    require(quantity <= blockedQuantity, s"quantity $quantity exceeds blocked $blockedQuantity")
    val updated = copy(
      availableQuantity = availableQuantity + quantity,
      blockedQuantity = blockedQuantity - quantity
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Unblocked(id, quantity, at)
    (updated, event)

  /** Adjusts quantity for SOX-compliant corrections. Modifies onHand and available. */
  def adjust(
      delta: Int,
      reasonCode: AdjustmentReasonCode,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Adjusted) =
    val updated = copy(
      onHandQuantity = onHandQuantity + delta,
      availableQuantity = availableQuantity + delta
    )
    updated.validateInvariant()
    val event = HandlingUnitStockEvent.Adjusted(id, delta, reasonCode, at)
    (updated, event)

  /** Changes the inventory status. */
  def changeStatus(
      newStatus: InventoryStatus,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.StatusChanged) =
    val previousStatus = status
    val updated = copy(status = newStatus)
    val event = HandlingUnitStockEvent.StatusChanged(id, previousStatus, newStatus, at)
    (updated, event)

/** Factory for [[HandlingUnitStock]]. */
object HandlingUnitStock:

  /** Creates a new handling unit stock with all quantity in the available bucket. */
  def create(
      containerId: ContainerId,
      slotCode: SlotCode,
      stockPositionId: StockPositionId,
      physicalContainer: Boolean,
      onHandQuantity: Int,
      at: Instant
  ): (HandlingUnitStock, HandlingUnitStockEvent.Created) =
    require(onHandQuantity >= 0, s"onHandQuantity must be non-negative, got $onHandQuantity")
    val id = HandlingUnitStockId()
    val hus = HandlingUnitStock(
      id = id,
      containerId = containerId,
      slotCode = slotCode,
      stockPositionId = stockPositionId,
      physicalContainer = physicalContainer,
      status = InventoryStatus.Available,
      onHandQuantity = onHandQuantity,
      availableQuantity = onHandQuantity,
      allocatedQuantity = 0,
      reservedQuantity = 0,
      blockedQuantity = 0
    )
    val event = HandlingUnitStockEvent.Created(
      id,
      containerId,
      slotCode,
      stockPositionId,
      physicalContainer,
      onHandQuantity,
      at
    )
    (hus, event)
