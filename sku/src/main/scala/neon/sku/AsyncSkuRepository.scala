package neon.sku

import neon.common.SkuId

import scala.concurrent.Future

/** Async port trait for [[Sku]] reference data queries (read-only). */
trait AsyncSkuRepository:
  def findById(id: SkuId): Future[Option[Sku]]
