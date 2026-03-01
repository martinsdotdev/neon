package neon.wave

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
    def release(): (Released, WaveEvent.WaveReleased) =
      val released = Released(id, orderGrouping, orderIds)
      val event = WaveEvent.WaveReleased(id, orderGrouping, orderIds, Instant.now())
      (released, event)

    def cancel(): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, Instant.now())
      (cancelled, event)

  case class Released(
      id: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId]
  ) extends Wave:
    def complete(): (Completed, WaveEvent.WaveCompleted) =
      val completed = Completed(id, orderGrouping)
      val event = WaveEvent.WaveCompleted(id, orderGrouping, Instant.now())
      (completed, event)

    def cancel(): (Cancelled, WaveEvent.WaveCancelled) =
      val cancelled = Cancelled(id, orderGrouping)
      val event = WaveEvent.WaveCancelled(id, orderGrouping, Instant.now())
      (cancelled, event)

  case class Completed(id: WaveId, orderGrouping: OrderGrouping) extends Wave

  case class Cancelled(id: WaveId, orderGrouping: OrderGrouping) extends Wave
