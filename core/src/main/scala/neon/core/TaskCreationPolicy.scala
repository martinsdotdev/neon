package neon.core

import neon.task.{Task, TaskEvent, TaskType}
import neon.wave.TaskRequest

import java.time.Instant

/** Creates [[Task.Planned]] tasks from [[TaskRequest]] items produced by wave planning.
  *
  * Each task request maps to exactly one planned pick task. The wave id is propagated from the
  * request for downstream completion detection.
  */
object TaskCreationPolicy:

  /** Transforms task requests into planned tasks.
    *
    * @param taskRequests
    *   the requests produced by wave release
    * @param at
    *   instant of the task creation
    * @return
    *   planned tasks paired with their creation events
    */
  def apply(
      taskRequests: List[TaskRequest],
      at: Instant
  ): List[(Task.Planned, TaskEvent.TaskCreated)] =
    taskRequests.map: req =>
      Task.create(
        taskType = TaskType.Pick,
        skuId = req.skuId,
        packagingLevel = req.packagingLevel,
        requestedQty = req.quantity,
        orderId = req.orderId,
        waveId = Some(req.waveId),
        parentTaskId = None,
        handlingUnitId = None,
        at = at
      )
