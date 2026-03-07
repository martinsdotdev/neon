package neon.order

import neon.common.OrderId

/** Port trait for [[Order]] reference data queries (read-only). */
trait OrderRepository:

  /** Finds an order by its unique identifier.
    *
    * @param id
    *   the order identifier
    * @return
    *   the order if it exists, [[None]] otherwise
    */
  def findById(id: OrderId): Option[Order]

  /** Finds all orders matching the given identifiers.
    *
    * @param ids
    *   the order identifiers to look up
    * @return
    *   the matching orders (may be fewer than requested)
    */
  def findByIds(ids: List[OrderId]): List[Order]
