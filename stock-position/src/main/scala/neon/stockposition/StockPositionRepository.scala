package neon.stockposition

import neon.common.{SkuId, StockPositionId, WarehouseAreaId}

/** Port trait for [[StockPosition]] aggregate persistence and queries. */
trait StockPositionRepository:

  /** Finds a stock position by its unique identifier. */
  def findById(id: StockPositionId): Option[StockPosition]

  /** Finds all stock positions for a given SKU in a warehouse area. */
  def findBySkuAndArea(skuId: SkuId, warehouseAreaId: WarehouseAreaId): List[StockPosition]

  /** Persists a stock position along with the event that caused the state change. */
  def save(stockPosition: StockPosition, event: StockPositionEvent): Unit
