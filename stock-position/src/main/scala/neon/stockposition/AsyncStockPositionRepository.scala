package neon.stockposition

import neon.common.{SkuId, StockPositionId, WarehouseAreaId}

import scala.concurrent.Future

/** Async port trait for [[StockPosition]] aggregate persistence and queries. */
trait AsyncStockPositionRepository:
  def findById(id: StockPositionId): Future[Option[StockPosition]]
  def findBySkuAndArea(skuId: SkuId, warehouseAreaId: WarehouseAreaId): Future[List[StockPosition]]
  def save(stockPosition: StockPosition, event: StockPositionEvent): Future[Unit]
