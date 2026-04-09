package neon.core

import neon.common.{CountTaskId, LocationId, SkuId}
import neon.counttask.{CountTask, CountTaskEvent}
import neon.cyclecount.CycleCount

import java.time.Instant

/** Stateless policy that creates [[CountTask]] instances from an in-progress [[CycleCount]].
  *
  * For each SKU-location pair in the stock snapshot that matches a SKU in the cycle count, a
  * [[CountTask.Pending]] is produced with the snapshot quantity as the expected value.
  */
object CountCreationPolicy:

  /** Creates count tasks for the given cycle count based on stock position snapshots.
    *
    * @param cycleCount
    *   the in-progress cycle count that tasks are being created for
    * @param stockSnapshots
    *   map of (SKU, location) to expected on-hand quantity
    * @param at
    *   instant of the creation
    * @return
    *   list of (pending count task, creation event) pairs
    */
  def apply(
      cycleCount: CycleCount.InProgress,
      stockSnapshots: Map[(SkuId, LocationId), Int],
      at: Instant
  ): List[(CountTask.Pending, CountTaskEvent.CountTaskCreated)] =
    val cycleCountSkuIds = cycleCount.skuIds.toSet
    stockSnapshots.toList
      .filter { case ((skuId, _), _) => cycleCountSkuIds.contains(skuId) }
      .map { case ((skuId, locationId), expectedQuantity) =>
        val id = CountTaskId()
        val pending =
          CountTask.Pending(id, cycleCount.id, skuId, locationId, expectedQuantity)
        val event = CountTaskEvent.CountTaskCreated(
          id,
          cycleCount.id,
          skuId,
          locationId,
          expectedQuantity,
          at
        )
        (pending, event)
      }
