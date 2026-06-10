package neon.core

import neon.common.{SkuId, StockPositionId, WarehouseAreaId}
import neon.stockposition.{AsyncStockPositionRepository, StockPosition, StockPositionEvent}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryAsyncStockPositionRepository(recorder: CallRecorder = CallRecorder())
    extends AsyncStockPositionRepository:
  val store: mutable.Map[StockPositionId, StockPosition] = mutable.Map.empty
  val events: mutable.ListBuffer[StockPositionEvent] = mutable.ListBuffer.empty

  def findById(id: StockPositionId): Future[Option[StockPosition]] =
    recorder.record("stockPosition.findById")
    Future.successful(store.get(id))

  def findBySkuAndArea(
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId
  ): Future[List[StockPosition]] =
    recorder.record("stockPosition.findBySkuAndArea")
    Future.successful(
      store.values
        .filter(position => position.skuId == skuId && position.warehouseAreaId == warehouseAreaId)
        .toList
    )

  def save(stockPosition: StockPosition, event: StockPositionEvent): Future[Unit] =
    recorder.record("stockPosition.save")
    store(stockPosition.id) = stockPosition
    events += event
    Future.unit
