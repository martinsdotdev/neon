package neon.counttask

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{CountTaskId, CycleCountId, LocationId, SkuId, UserId}

import java.time.Instant

/** Typestate-encoded aggregate for individual count task lifecycle management.
  *
  * A count task represents a single SKU-location count within a cycle count. The state machine
  * follows: [[CountTask.Pending]] -> [[CountTask.Assigned]] -> [[CountTask.Recorded]], with
  * [[CountTask.Cancelled]] reachable from any non-terminal state. Transitions are only available on
  * valid source states, enforced at compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait CountTask:
  /** The unique identifier of this count task. */
  def id: CountTaskId

  /** The cycle count this task belongs to. */
  def cycleCountId: CycleCountId

  /** The SKU to be counted. */
  def skuId: SkuId

  /** The location where the count takes place. */
  def locationId: LocationId

  /** The expected quantity from the stock position snapshot. */
  def expectedQuantity: Int

/** Factory and state definitions for the [[CountTask]] aggregate. */
object CountTask:

  /** A count task that has been created but not yet assigned to a counter.
    *
    * Transitions: [[assign]] -> [[Assigned]], [[cancel]] -> [[Cancelled]].
    *
    * @param id
    *   unique count task identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the SKU to count
    * @param locationId
    *   the location to count at
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    */
  case class Pending(
      id: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int
  ) extends CountTask:

    /** Assigns this count task to a user, transitioning from [[Pending]] to [[Assigned]].
      *
      * @param userId
      *   the user who will perform the count
      * @param at
      *   instant of the assignment
      * @return
      *   assigned state and assignment event
      */
    def assign(
        userId: UserId,
        at: Instant
    ): (Assigned, CountTaskEvent.CountTaskAssigned) =
      val assigned = Assigned(id, cycleCountId, skuId, locationId, expectedQuantity, userId)
      val event = CountTaskEvent.CountTaskAssigned(id, userId, at)
      (assigned, event)

    /** Cancels this pending count task.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, CountTaskEvent.CountTaskCancelled) =
      val cancelled = Cancelled(id, cycleCountId, skuId, locationId, expectedQuantity, None)
      val event = CountTaskEvent.CountTaskCancelled(id, cycleCountId, at)
      (cancelled, event)

  /** A count task assigned to a user and ready for counting.
    *
    * Transitions: [[record]] -> [[Recorded]], [[cancel]] -> [[Cancelled]].
    *
    * @param id
    *   unique count task identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the SKU to count
    * @param locationId
    *   the location to count at
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    * @param assignedTo
    *   the user performing the count
    */
  case class Assigned(
      id: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int,
      assignedTo: UserId
  ) extends CountTask:

    /** Records the count result, transitioning from [[Assigned]] to [[Recorded]].
      *
      * Variance is computed as `actualQuantity - expectedQuantity`: positive means surplus,
      * negative means shortage.
      *
      * @param actualQuantity
      *   the quantity actually counted
      * @param at
      *   instant of the recording
      * @return
      *   recorded state and recording event
      */
    def record(
        actualQuantity: Int,
        at: Instant
    ): (Recorded, CountTaskEvent.CountTaskRecorded) =
      val variance = actualQuantity - expectedQuantity
      val recorded =
        Recorded(
          id,
          cycleCountId,
          skuId,
          locationId,
          expectedQuantity,
          assignedTo,
          actualQuantity,
          variance
        )
      val event = CountTaskEvent.CountTaskRecorded(
        id,
        cycleCountId,
        skuId,
        locationId,
        expectedQuantity,
        actualQuantity,
        variance,
        assignedTo,
        at
      )
      (recorded, event)

    /** Cancels this assigned count task.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, CountTaskEvent.CountTaskCancelled) =
      val cancelled =
        Cancelled(id, cycleCountId, skuId, locationId, expectedQuantity, Some(assignedTo))
      val event = CountTaskEvent.CountTaskCancelled(id, cycleCountId, at)
      (cancelled, event)

  /** A count task that has been completed with a recorded result. Terminal state.
    *
    * @param id
    *   unique count task identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the counted SKU
    * @param locationId
    *   the counted location
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    * @param assignedTo
    *   the user who performed the count
    * @param actualQuantity
    *   the quantity actually counted
    * @param variance
    *   the difference: actual - expected
    */
  case class Recorded(
      id: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int,
      assignedTo: UserId,
      actualQuantity: Int,
      variance: Int
  ) extends CountTask

  /** A count task cancelled from any non-terminal state. Terminal state.
    *
    * @param id
    *   unique count task identifier
    * @param cycleCountId
    *   the parent cycle count
    * @param skuId
    *   the SKU that was to be counted
    * @param locationId
    *   the location that was to be counted
    * @param expectedQuantity
    *   the expected quantity from the stock position snapshot
    * @param assignedTo
    *   the assigned user, if any
    */
  case class Cancelled(
      id: CountTaskId,
      cycleCountId: CycleCountId,
      skuId: SkuId,
      locationId: LocationId,
      expectedQuantity: Int,
      assignedTo: Option[UserId]
  ) extends CountTask
