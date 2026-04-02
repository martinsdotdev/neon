package neon.order

import neon.common.OrderId

import scala.concurrent.Future

/** Async port trait for [[Order]] reference data queries (read-only). */
trait AsyncOrderRepository:
  def findById(id: OrderId): Future[Option[Order]]
  def findByIds(ids: List[OrderId]): Future[List[Order]]
