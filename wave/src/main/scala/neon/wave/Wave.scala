package neon.wave

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{OrderId, WaveId}

import java.time.Instant

/** Typestate-encoded aggregate for wave lifecycle management.
  *
  * A wave groups orders for batch processing through the warehouse. The state machine follows:
  * [[Wave.Planned]] -> [[Wave.Released]] -> [[Wave.Completed]], with [[Wave.Cancelled]] reachable
  * from any non-terminal state. Transitions are only available on valid source states, enforced at
  * compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait Wave:
  /** The unique identifier of this wave. */
  def id: WaveId

  /** The grouping strategy applied to orders in this wave. */
  def orderGrouping: OrderGrouping

/** Factory and state definitions for the [[Wave]] aggregate. */
object Wave:

  /** A wave that has been planned but not yet released for execution.
    *
    * @param id
    *   unique wave identifier
    * @param orderGrouping
    *   grouping strategy for the wave's orders
    * @param orderIds
    *   orders included in this wave
    */
  case class Planned(
      id: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId]
  ) extends Wave:

    /** Releases the wave, transitioning from [[Planned]] to [[Released]].
      *
      * @param at
      *   instant of the release
      * @return
      *   released state and release event
      */
    def release(at: Instant): (Released, WaveEvent.WaveReleased) =
      val released = Released(id, orderGrouping, orderIds)
      val event = WaveEvent.WaveReleased(id, orderGrouping, orderIds, at)
      (released, event)

    /** Cancels this planned wave before release.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, at)
      (cancelled, event)

  /** A wave that has been released and whose tasks are in progress.
    *
    * @param id
    *   unique wave identifier
    * @param orderGrouping
    *   grouping strategy for the wave's orders
    * @param orderIds
    *   orders included in this wave
    */
  case class Released(
      id: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId]
  ) extends Wave:

    /** Completes the wave, transitioning from [[Released]] to [[Completed]].
      *
      * @param at
      *   instant of the completion
      * @return
      *   completed state and completion event
      */
    def complete(at: Instant): (Completed, WaveEvent.WaveCompleted) =
      val completed = Completed(id, orderGrouping)
      val event = WaveEvent.WaveCompleted(id, orderGrouping, at)
      (completed, event)

    /** Cancels this released wave, aborting in-progress work.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, at)
      (cancelled, event)

  /** A wave whose tasks have all been completed. Terminal state. */
  case class Completed(id: WaveId, orderGrouping: OrderGrouping) extends Wave

  /** A wave that was cancelled before completion. Terminal state. */
  case class Cancelled(id: WaveId, orderGrouping: OrderGrouping) extends Wave
