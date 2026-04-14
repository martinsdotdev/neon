package neon.core

import neon.common.{SkuId, StockPositionId, WarehouseAreaId}
import neon.stockposition.{StockPosition, StockPositionEvent, StockPositionRepository}

import scala.collection.mutable

class InMemoryStockPositionRepository extends StockPositionRepository:
  val store: mutable.Map[StockPositionId, StockPosition] = mutable.Map.empty
  val events: mutable.ListBuffer[StockPositionEvent] = mutable.ListBuffer.empty
  def findById(id: StockPositionId): Option[StockPosition] = store.get(id)
  def findBySkuAndArea(
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId
  ): List[StockPosition] =
    store.values
      .filter(sp => sp.skuId == skuId && sp.warehouseAreaId == warehouseAreaId)
      .toList
  def save(stockPosition: StockPosition, event: StockPositionEvent): Unit =
    store(stockPosition.id) = stockPosition
    events += event
