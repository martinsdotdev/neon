package neon.core

import neon.common.{
  GoodsReceiptId,
  InboundDeliveryId,
  LotAttributes,
  OrderId,
  PackagingLevel,
  SkuId,
  StockPositionId,
  TaskId,
  WarehouseAreaId
}
import neon.goodsreceipt.{GoodsReceipt, GoodsReceiptEvent, GoodsReceiptRepository, ReceivedLine}
import neon.stockposition.{StockPosition, StockPositionEvent, StockPositionRepository}
import neon.task.{Task, TaskEvent, TaskRepository, TaskType}
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant
import scala.collection.mutable

class InboundReceivingServiceSuite extends AnyFunSpec with EitherValues:
  val goodsReceiptId = GoodsReceiptId()
  val inboundDeliveryId = InboundDeliveryId()
  val skuId = SkuId()
  val orderId = OrderId()
  val warehouseAreaId = WarehouseAreaId()
  val at = Instant.now()

  def confirmedReceipt(
      lines: List[ReceivedLine] = List(
        ReceivedLine(skuId, 10, PackagingLevel.Each, LotAttributes(), None)
      )
  ): GoodsReceipt.Confirmed =
    GoodsReceipt.Confirmed(goodsReceiptId, inboundDeliveryId, lines)

  def stockPosition(
      skuId: SkuId = skuId,
      onHand: Int = 50
  ): StockPosition =
    val (sp, _) =
      StockPosition.create(skuId, warehouseAreaId, LotAttributes(), onHand, at)
    sp

  class InMemoryGoodsReceiptRepository extends GoodsReceiptRepository:
    val store: mutable.Map[GoodsReceiptId, GoodsReceipt] = mutable.Map.empty
    val events: mutable.ListBuffer[GoodsReceiptEvent] = mutable.ListBuffer.empty
    def findById(id: GoodsReceiptId): Option[GoodsReceipt] = store.get(id)
    def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Unit =
      store(receipt.id) = receipt
      events += event

  class InMemoryTaskRepository extends TaskRepository:
    val store: mutable.Map[TaskId, Task] = mutable.Map.empty
    val events: mutable.ListBuffer[TaskEvent] = mutable.ListBuffer.empty
    def findById(id: TaskId): Option[Task] = store.get(id)
    def findByWaveId(waveId: neon.common.WaveId): List[Task] = List.empty
    def findByHandlingUnitId(
        handlingUnitId: neon.common.HandlingUnitId
    ): List[Task] = List.empty
    def save(task: Task, event: TaskEvent): Unit =
      store(task.id) = task
      events += event
    def saveAll(entries: List[(Task, TaskEvent)]): Unit =
      entries.foreach { (task, event) => save(task, event) }

  class InMemoryStockPositionRepository extends StockPositionRepository:
    val store: mutable.Map[StockPositionId, StockPosition] = mutable.Map.empty
    val events: mutable.ListBuffer[StockPositionEvent] = mutable.ListBuffer.empty
    def findById(id: StockPositionId): Option[StockPosition] = store.get(id)
    def findBySkuAndArea(
        skuId: SkuId,
        warehouseAreaId: WarehouseAreaId
    ): List[StockPosition] =
      store.values.filter(sp => sp.skuId == skuId && sp.warehouseAreaId == warehouseAreaId).toList
    def save(sp: StockPosition, event: StockPositionEvent): Unit =
      store(sp.id) = sp
      events += event

  def buildService(
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      stockPositionRepository: StockPositionRepository = InMemoryStockPositionRepository()
  ): InboundReceivingService =
    InboundReceivingService(taskRepository, stockPositionRepository)

  describe("InboundReceivingService"):
    describe("task creation"):
      it("creates putaway tasks from confirmed goods receipt lines"):
        val taskRepository = InMemoryTaskRepository()
        val service = buildService(taskRepository = taskRepository)
        val receipt = confirmedReceipt()
        val result = service.processConfirmedReceipt(receipt, orderId, at)
        assert(result.tasks.size == 1)
        val (planned, _) = result.tasks.head
        assert(planned.taskType == TaskType.Putaway)
        assert(planned.skuId == skuId)
        assert(planned.requestedQuantity == 10)

      it("creates one task per received line"):
        val taskRepository = InMemoryTaskRepository()
        val skuId2 = SkuId()
        val lines = List(
          ReceivedLine(skuId, 10, PackagingLevel.Each, LotAttributes(), None),
          ReceivedLine(skuId2, 5, PackagingLevel.Case, LotAttributes(), None)
        )
        val service = buildService(taskRepository = taskRepository)
        val result = service.processConfirmedReceipt(confirmedReceipt(lines), orderId, at)
        assert(result.tasks.size == 2)
        assert(taskRepository.store.size == 2)

      it("persists all created tasks"):
        val taskRepository = InMemoryTaskRepository()
        val service = buildService(taskRepository = taskRepository)
        service.processConfirmedReceipt(confirmedReceipt(), orderId, at)
        assert(taskRepository.store.size == 1)
        assert(taskRepository.events.size == 1)

    describe("stock position updates"):
      it("adds received quantity to existing stock position"):
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = stockPosition()
        stockPositionRepository.store(sp.id) = sp
        val service = buildService(
          stockPositionRepository = stockPositionRepository
        )
        val receipt = confirmedReceipt()
        val result = service.processConfirmedReceipt(
          receipt,
          orderId,
          at,
          stockPositionId = Some(sp.id)
        )
        assert(result.stockPositionUpdates.size == 1)
        val (updatedSp, _) = result.stockPositionUpdates.head
        assert(updatedSp.onHandQuantity == 60)
        assert(updatedSp.availableQuantity == 60)

      it("skips stock position update when no stockPositionId provided"):
        val stockPositionRepository = InMemoryStockPositionRepository()
        val service = buildService(
          stockPositionRepository = stockPositionRepository
        )
        val result = service.processConfirmedReceipt(confirmedReceipt(), orderId, at)
        assert(result.stockPositionUpdates.isEmpty)

    describe("result"):
      it("carries tasks and stock position updates"):
        val service = buildService()
        val result = service.processConfirmedReceipt(confirmedReceipt(), orderId, at)
        assert(result.tasks.nonEmpty)
        assert(result.receipt == confirmedReceipt())
