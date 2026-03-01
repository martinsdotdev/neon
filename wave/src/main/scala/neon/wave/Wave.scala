package neon.wave

import neon.common.{OrderId, WaveId}
import java.time.Instant

sealed trait Wave:
  def id: WaveId
  def orderGrouping: OrderGrouping

object Wave:
  case class Planned(
      id: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId]
  ) extends Wave:
    def release(at: Instant): (Released, WaveEvent.WaveReleased) =
      val released = Released(id, orderGrouping, orderIds)
      val event = WaveEvent.WaveReleased(id, orderGrouping, orderIds, at)
      (released, event)

    def cancel(at: Instant): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, at)
      (cancelled, event)

  case class Released(
      id: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId]
  ) extends Wave:
    def complete(at: Instant): (Completed, WaveEvent.WaveCompleted) =
      val completed = Completed(id, orderGrouping)
      val event = WaveEvent.WaveCompleted(id, orderGrouping, at)
      (completed, event)

    def cancel(at: Instant): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, at)
      (cancelled, event)

  case class Completed(id: WaveId, orderGrouping: OrderGrouping) extends Wave

  case class Cancelled(id: WaveId, orderGrouping: OrderGrouping) extends Wave
