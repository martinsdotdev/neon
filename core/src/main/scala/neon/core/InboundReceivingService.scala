package neon.core

import neon.common.{OrderId, StockPositionId}
import neon.goodsreceipt.GoodsReceipt
import neon.stockposition.{StockPosition, StockPositionEvent, StockPositionRepository}
import neon.task.{Task, TaskEvent, TaskRepository}

import java.time.Instant

/** The result of processing a confirmed goods receipt through the inbound receiving flow.
  *
  * @param receipt
  *   the confirmed goods receipt
  * @param tasks
  *   putaway tasks created from the receipt's received lines
  * @param stockPositionUpdates
  *   stock position updates from adding received quantities
  */
case class InboundReceivingResult(
    receipt: GoodsReceipt.Confirmed,
    tasks: List[(Task.Planned, TaskEvent.TaskCreated)],
    stockPositionUpdates: List[(StockPosition, StockPositionEvent.QuantityAdded)]
)

/** Orchestrates the inbound receiving flow: creates putaway tasks from confirmed goods receipt
  * lines and optionally updates stock positions with received quantities.
  *
  * @param taskRepository
  *   repository for task persistence
  * @param stockPositionRepository
  *   repository for stock position persistence
  */
class InboundReceivingService(
    taskRepository: TaskRepository,
    stockPositionRepository: StockPositionRepository
):

  /** Processes a confirmed goods receipt by creating putaway tasks and updating stock.
    *
    * Steps: (1) create planned putaway tasks from received lines via [[PutawayCreationPolicy]], (2)
    * optionally add received quantities to the specified stock position.
    *
    * @param receipt
    *   the confirmed goods receipt
    * @param orderId
    *   the order identifier for task creation
    * @param at
    *   instant of the processing
    * @param stockPositionId
    *   optional stock position to update with received quantities
    * @return
    *   the processing result with created tasks and stock position updates
    */
  def processConfirmedReceipt(
      receipt: GoodsReceipt.Confirmed,
      orderId: OrderId,
      at: Instant,
      stockPositionId: Option[StockPositionId] = None
  ): InboundReceivingResult =
    val tasks = PutawayCreationPolicy(receipt.receivedLines, orderId, at)
    taskRepository.saveAll(tasks)

    val stockPositionUpdates = stockPositionId match
      case Some(spId) =>
        stockPositionRepository.findById(spId) match
          case Some(sp) =>
            val totalQuantity = receipt.receivedLines.map(_.quantity).sum
            val (updated, event) = sp.addQuantity(totalQuantity, at)
            stockPositionRepository.save(updated, event)
            List((updated, event))
          case None => List.empty
      case None => List.empty

    InboundReceivingResult(
      receipt = receipt,
      tasks = tasks,
      stockPositionUpdates = stockPositionUpdates
    )
