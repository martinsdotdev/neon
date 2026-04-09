package neon.cyclecount

import neon.common.serialization.CborSerializable
import neon.common.{CountMethod, CountType, CycleCountId, SkuId, WarehouseAreaId}

import java.time.Instant

/** Domain events emitted by [[CycleCount]] state transitions. */
sealed trait CycleCountEvent extends CborSerializable:
  /** The cycle count that emitted this event. */
  def cycleCountId: CycleCountId

  /** The type of cycle count. */
  def countType: CountType

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for the [[CycleCount]] aggregate. */
object CycleCountEvent:

  /** Emitted when a new cycle count is created.
    *
    * @param cycleCountId
    *   the created cycle count's identifier
    * @param warehouseAreaId
    *   the warehouse area targeted by this count
    * @param skuIds
    *   the SKUs to be counted
    * @param countType
    *   the type of cycle count
    * @param countMethod
    *   the counting method
    * @param occurredAt
    *   instant of the creation
    */
  case class CycleCountCreated(
      cycleCountId: CycleCountId,
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod,
      occurredAt: Instant
  ) extends CycleCountEvent

  /** Emitted when a cycle count transitions from New to InProgress.
    *
    * @param cycleCountId
    *   the started cycle count's identifier
    * @param countType
    *   the type of cycle count
    * @param countMethod
    *   the counting method
    * @param occurredAt
    *   instant of the start
    */
  case class CycleCountStarted(
      cycleCountId: CycleCountId,
      countType: CountType,
      countMethod: CountMethod,
      occurredAt: Instant
  ) extends CycleCountEvent

  /** Emitted when all count tasks in a cycle count have been completed.
    *
    * @param cycleCountId
    *   the completed cycle count's identifier
    * @param countType
    *   the type of cycle count
    * @param occurredAt
    *   instant of the completion
    */
  case class CycleCountCompleted(
      cycleCountId: CycleCountId,
      countType: CountType,
      occurredAt: Instant
  ) extends CycleCountEvent

  /** Emitted when a cycle count is cancelled from any non-terminal state.
    *
    * @param cycleCountId
    *   the cancelled cycle count's identifier
    * @param countType
    *   the type of cycle count
    * @param occurredAt
    *   instant of the cancellation
    */
  case class CycleCountCancelled(
      cycleCountId: CycleCountId,
      countType: CountType,
      occurredAt: Instant
  ) extends CycleCountEvent
