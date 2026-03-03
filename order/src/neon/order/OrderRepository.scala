package neon.order

import neon.common.OrderId

/** Port trait for Order reference data queries (read-only). */
trait OrderRepository:
  def findById(id: OrderId): Option[Order]
  def findByIds(ids: List[OrderId]): List[Order]
