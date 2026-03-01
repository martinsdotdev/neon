package neon.wave

import neon.common.OrderId
import java.time.Instant

sealed trait WaveEvent:
  def waveId: WaveId
  def orderGrouping: OrderGrouping
  def occurredAt: Instant

object WaveEvent:
  case class WaveReleased(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      orderIds: List[OrderId],
      occurredAt: Instant
  ) extends WaveEvent

  case class WaveCompleted(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent

  case class WaveCancelled(
      waveId: WaveId,
      orderGrouping: OrderGrouping,
      occurredAt: Instant
  ) extends WaveEvent
