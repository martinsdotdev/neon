package neon.core

import neon.common.{CountTaskId, LocationId, SkuId, UserId}

/** Value object representing a variance discovered during cycle counting. Created for each count
  * task that has a non-zero difference between expected and actual quantities.
  *
  * @param countTaskId
  *   the count task that produced this variance
  * @param skuId
  *   the SKU that was counted
  * @param locationId
  *   the location where the count took place
  * @param expectedQuantity
  *   the expected quantity from the stock position snapshot
  * @param actualQuantity
  *   the quantity actually counted
  * @param variance
  *   the difference: actual - expected (positive = surplus, negative = shortage)
  * @param countedBy
  *   the user who performed the count
  */
case class CountVariance(
    countTaskId: CountTaskId,
    skuId: SkuId,
    locationId: LocationId,
    expectedQuantity: Int,
    actualQuantity: Int,
    variance: Int,
    countedBy: UserId
)
