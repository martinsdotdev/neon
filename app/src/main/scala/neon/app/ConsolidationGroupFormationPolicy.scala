package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.wave.{OrderGrouping, WaveEvent}

import java.time.Instant

object ConsolidationGroupFormationPolicy:
  def apply(
      event: WaveEvent.WaveReleased,
      at: Instant
  ): List[(ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)] =
    event.orderGrouping match
      case OrderGrouping.Multi =>
        List(ConsolidationGroup.create(event.waveId, event.orderIds, at))
      case OrderGrouping.Single => Nil
