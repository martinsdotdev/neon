package neon.core

import neon.common.TaskId
import neon.consolidationgroup.ConsolidationGroup
import neon.stockposition.{StockPosition, StockPositionEvent}
import neon.task.Task
import neon.wave.Wave

import java.time.Instant

/** Pure decision module for the task completion cascade: complete the task, consume or deallocate
  * allocated stock, create a shortpick replacement, route the handling unit, and detect wave and
  * picking completion. All decisions, zero I/O.
  *
  * Shells load a [[CascadeState]] before calling [[decide]] and persist the outcome afterwards.
  * Shell contract: all loads happen before [[decide]]; no shell reads its own writes. The module
  * normalises the loaded task set itself (substituting the freshly completed task and appending
  * the shortpick replacement), so sync and async shells decide identically regardless of how stale
  * their loaded view is.
  */
object TaskCompletionCascade:

  /** Everything the cascade decision needs, pre-loaded by a shell.
    *
    * @param stockPosition
    *   the task's allocated stock position, when the task carries one and the shell tracks stock
    * @param wave
    *   the task's wave in whatever state the load returned; only a [[Wave.Released]] can complete
    * @param waveTasks
    *   the tasks of the wave as loaded; may still contain the completing task in its pre-completion
    *   state, or miss it entirely on a stale read
    * @param consolidationGroups
    *   the consolidation groups of the wave
    */
  case class CascadeState(
      stockPosition: Option[StockPosition],
      wave: Option[Wave],
      waveTasks: List[Task],
      consolidationGroups: List[ConsolidationGroup]
  )

  object CascadeState:
    val empty: CascadeState =
      CascadeState(stockPosition = None, wave = None, waveTasks = Nil, consolidationGroups = Nil)

  /** Decision output: the result exposed to callers plus the ordered stock writes.
    *
    * A partial pick produces two stock writes (consume the actual quantity, then deallocate the
    * remainder) that must be persisted in order; `result.stockConsumption` always equals
    * `stockWrites.lastOption`.
    */
  case class Outcome(
      result: TaskCompletionResult,
      stockWrites: List[(StockPosition, StockPositionEvent)]
  )

  /** Validation gate: quantity, existence, Assigned state, and the verification requirement. */
  def validate(
      taskId: TaskId,
      task: Option[Task],
      actualQuantity: Int,
      verified: Boolean,
      verificationProfile: VerificationProfile
  ): Either[TaskCompletionError, Task.Assigned] =
    if actualQuantity < 0 then
      Left(TaskCompletionError.InvalidActualQuantity(taskId, actualQuantity))
    else
      task match
        case None                          => Left(TaskCompletionError.TaskNotFound(taskId))
        case Some(assigned: Task.Assigned) =>
          if verificationProfile.requiresVerification(assigned.packagingLevel) && !verified
          then Left(TaskCompletionError.VerificationRequired(taskId))
          else Right(assigned)
        case Some(_) => Left(TaskCompletionError.TaskNotAssigned(taskId))

  /** Runs the full cascade decision over pre-loaded state. Total: a validated input cannot fail. */
  def decide(
      assigned: Task.Assigned,
      actualQuantity: Int,
      at: Instant,
      state: CascadeState
  ): Outcome =
    val (completed, completedEvent) = assigned.complete(actualQuantity, at)
    val stockWrites = stockWritesFor(completed, state.stockPosition, at)
    val shortpick = ShortpickPolicy(completed, at)
    val routing = RoutingPolicy(completedEvent, at)

    val (waveCompletion, pickingCompletion) = completed.waveId match
      case None    => (None, None)
      case Some(_) =>
        // Canonical post-completion task set: represent the completing task by its Completed
        // state whatever the load returned, and include the shortpick replacement so an open
        // remainder suppresses wave and picking completion in every shell.
        val effectiveWaveTasks =
          state.waveTasks.filterNot(_.id == completed.id) ++
            (completed :: shortpick.map(_._1).toList)

        val waveCompletion = state.wave
          .collect { case released: Wave.Released =>
            WaveCompletionPolicy(effectiveWaveTasks, released, at)
          }
          .flatten

        val pickingCompletion = state.consolidationGroups
          .collectFirst {
            case group: ConsolidationGroup.Created if group.orderIds.contains(completed.orderId) =>
              group
          }
          .flatMap { group =>
            val groupOrderIds = group.orderIds.toSet
            val groupTasks = effectiveWaveTasks.filter(task => groupOrderIds.contains(task.orderId))
            PickingCompletionPolicy(groupTasks, group, at)
          }

        (waveCompletion, pickingCompletion)

    Outcome(
      result = TaskCompletionResult(
        completed = completed,
        completedEvent = completedEvent,
        shortpick = shortpick,
        transportOrder = routing,
        waveCompletion = waveCompletion,
        pickingCompletion = pickingCompletion,
        stockConsumption = stockWrites.lastOption
      ),
      stockWrites = stockWrites
    )

  /** Consumes allocated stock for the actual quantity and deallocates any remainder back to
    * available. No writes when the task has no loaded stock position.
    */
  private def stockWritesFor(
      completed: Task.Completed,
      stockPosition: Option[StockPosition],
      at: Instant
  ): List[(StockPosition, StockPositionEvent)] =
    stockPosition match
      case None                => Nil
      case Some(stockPosition) =>
        val remainder = completed.requestedQuantity - completed.actualQuantity
        if completed.actualQuantity > 0 && remainder > 0 then
          // Partial pick: consume actual, then deallocate remainder
          val (afterConsume, consumeEvent) =
            stockPosition.consumeAllocated(completed.actualQuantity, at)
          val (afterDeallocate, deallocateEvent) = afterConsume.deallocate(remainder, at)
          List((afterConsume, consumeEvent), (afterDeallocate, deallocateEvent))
        else if completed.actualQuantity > 0 then
          // Full pick: consume all
          List(stockPosition.consumeAllocated(completed.actualQuantity, at))
        else if completed.requestedQuantity > 0 then
          // Zero pick (full shortpick): deallocate all back to available
          List(stockPosition.deallocate(completed.requestedQuantity, at))
        else Nil
