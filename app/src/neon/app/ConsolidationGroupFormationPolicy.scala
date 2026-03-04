package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent}
import neon.wave.{OrderGrouping, WaveEvent}

import java.time.Instant

/** Forms [[ConsolidationGroup.Created]] groups when a wave is released with [[OrderGrouping.Multi]]
  * grouping.
  *
  * Returns an empty list for [[OrderGrouping.Single]] waves, which do not require consolidation.
  */
object ConsolidationGroupFormationPolicy:

  /** Creates consolidation groups from the released wave event.
    *
    * @param event
    *   the wave release event carrying order grouping and order ids
    * @param at
    *   instant of the group creation
    * @return
    *   created groups paired with their creation events, or [[Nil]] for Single grouping
    */
  def apply(
      event: WaveEvent.WaveReleased,
      at: Instant
  ): List[(ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)] =
    event.orderGrouping match
      case OrderGrouping.Multi =>
        List(ConsolidationGroup.create(event.waveId, event.orderIds, at))
      case OrderGrouping.Single => Nil
