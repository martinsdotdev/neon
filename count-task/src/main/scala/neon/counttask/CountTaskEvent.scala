package neon.counttask

import neon.common.serialization.CborSerializable
import neon.common.{CountTaskId, CycleCountId, LocationId, SkuId, UserId}

import java.time.Instant

/** Domain events emitted by [[CountTask]] state transitions. */
sealed trait CountTaskEvent extends CborSerializable:
  /** The count task that emitted this event. */
  def countTaskId: CountTaskId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for the [[CountTask]] aggregate. */
object CountTaskEvent:

  /** Emitted when a new count task is created in the [[CountTask.Pending]] state.
    *
    * @param countTaskId
    *   the created count task's identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the SKU to be counted
    * @param locationId
    *   the location to count at
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    * @param occurredAt
    *   instant of the creation
    */
  case class CountTaskCreated(
      countTaskId: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int,
      occurredAt: Instant
  ) extends CountTaskEvent

  /** Emitted when a pending count task is assigned to a user.
    *
    * @param countTaskId
    *   the assigned count task's identifier
    * @param userId
    *   the user assigned to perform the count
    * @param occurredAt
    *   instant of the assignment
    */
  case class CountTaskAssigned(
      countTaskId: CountTaskId,
      userId: UserId,
      occurredAt: Instant
  ) extends CountTaskEvent

  /** Emitted when an assigned count task records its result.
    *
    * @param countTaskId
    *   the recorded count task's identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the counted SKU
    * @param locationId
    *   the counted location
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    * @param actualQuantity
    *   the quantity actually counted
    * @param variance
    *   the difference: actual - expected
    * @param countedBy
    *   the user who performed the count
    * @param occurredAt
    *   instant of the recording
    */
  case class CountTaskRecorded(
      countTaskId: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int,
      actualQuantity: Int,
      variance: Int,
      countedBy: UserId,
      occurredAt: Instant
  ) extends CountTaskEvent

  /** Emitted when a count task is cancelled from any non-terminal state.
    *
    * @param countTaskId
    *   the cancelled count task's identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param occurredAt
    *   instant of the cancellation
    */
  case class CountTaskCancelled(
      countTaskId: CountTaskId,
      cycleCountId: CycleCountId,
      occurredAt: Instant
  ) extends CountTaskEvent
