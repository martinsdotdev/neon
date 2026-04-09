package neon.core

import neon.common.OrderId
import neon.goodsreceipt.ReceivedLine
import neon.task.{Task, TaskEvent, TaskType}

import java.time.Instant

/** Creates [[Task.Planned]] tasks with [[TaskType.Putaway]] from confirmed goods receipt lines.
  *
  * Each received line maps to exactly one planned putaway task. Putaway tasks are not associated
  * with any wave, since they originate from the inbound flow rather than wave-driven outbound.
  */
object PutawayCreationPolicy:

  /** Transforms received lines into planned putaway tasks.
    *
    * @param receivedLines
    *   the lines from a confirmed goods receipt
    * @param orderId
    *   the order identifier for the inbound receiving
    * @param at
    *   instant of the task creation
    * @return
    *   planned tasks paired with their creation events
    */
  def apply(
      receivedLines: List[ReceivedLine],
      orderId: OrderId,
      at: Instant
  ): List[(Task.Planned, TaskEvent.TaskCreated)] =
    receivedLines.map: line =>
      Task.create(
        taskType = TaskType.Putaway,
        skuId = line.skuId,
        packagingLevel = line.packagingLevel,
        requestedQuantity = line.quantity,
        orderId = orderId,
        waveId = None,
        parentTaskId = None,
        handlingUnitId = None,
        at = at
      )
