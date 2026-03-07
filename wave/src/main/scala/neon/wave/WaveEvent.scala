package neon.wave

import neon.common.{OrderId, WaveId}

import java.time.Instant

/** Domain events emitted by [[Wave]] state transitions. */
sealed trait WaveEvent:
  /** The wave that emitted this event. */
  def waveId: WaveId

  /** The grouping strategy of the wave that emitted this event. */
  def orderGrouping: OrderGrouping

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for the [[Wave]] aggregate. */
object WaveEvent:

  /** Emitted when a planned wave is released with its orders.
    *
    * @param waveId
    *   the released wave's identifier
    * @param orderGrouping
    *   grouping strategy of the released wave
    * @param orderIds
    *   orders included in the release
    * @param occurredAt
    *   instant of the release
    */
  case class WaveReleased(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId],
      occurredAt: Instant
  ) extends WaveEvent

  /** Emitted when all tasks in a released wave have been completed.
    *
    * @param waveId
    *   the completed wave's identifier
    * @param orderGrouping
    *   grouping strategy of the completed wave
    * @param occurredAt
    *   instant of the completion
    */
  case class WaveCompleted(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent

  /** Emitted when a wave is cancelled from any non-terminal state.
    *
    * @param waveId
    *   the cancelled wave's identifier
    * @param orderGrouping
    *   grouping strategy of the cancelled wave
    * @param occurredAt
    *   instant of the cancellation
    */
  case class WaveCancelled(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent
