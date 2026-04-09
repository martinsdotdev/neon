package neon.cyclecount

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{CountMethod, CountType, CycleCountId, SkuId, WarehouseAreaId}

import java.time.Instant

/** Typestate-encoded aggregate for cycle count lifecycle management.
  *
  * A cycle count represents a scheduled or ad-hoc inventory verification for a set of SKUs within a
  * warehouse area. The state machine follows: [[CycleCount.New]] -> [[CycleCount.InProgress]] ->
  * [[CycleCount.Completed]], with [[CycleCount.Cancelled]] reachable from any non-terminal state.
  * Transitions are only available on valid source states, enforced at compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait CycleCount:
  /** The unique identifier of this cycle count. */
  def id: CycleCountId

  /** The warehouse area targeted by this cycle count. */
  def warehouseAreaId: WarehouseAreaId

  /** The SKUs to be counted. */
  def skuIds: List[SkuId]

  /** The type of cycle count (planned, random, triggered, or recount). */
  def countType: CountType

  /** The counting method (blind or informed). */
  def countMethod: CountMethod

/** Factory and state definitions for the [[CycleCount]] aggregate. */
object CycleCount:

  /** A cycle count that has been created but not yet started.
    *
    * Transitions: [[start]] -> [[InProgress]], [[cancel]] -> [[Cancelled]].
    *
    * @param id
    *   unique cycle count identifier
    * @param warehouseAreaId
    *   the warehouse area to count
    * @param skuIds
    *   the SKUs included in this count
    * @param countType
    *   the type of cycle count
    * @param countMethod
    *   the counting method
    */
  case class New(
      id: CycleCountId,
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod
  ) extends CycleCount:

    /** Starts the cycle count, transitioning from [[New]] to [[InProgress]].
      *
      * @param at
      *   instant of the start
      * @return
      *   in-progress state and start event
      */
    def start(at: Instant): (InProgress, CycleCountEvent.CycleCountStarted) =
      val inProgress = InProgress(id, warehouseAreaId, skuIds, countType, countMethod)
      val event = CycleCountEvent.CycleCountStarted(id, countType, countMethod, at)
      (inProgress, event)

    /** Cancels this cycle count before it starts.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, CycleCountEvent.CycleCountCancelled) =
      val cancelled = Cancelled(id, warehouseAreaId, skuIds, countType, countMethod)
      val event = CycleCountEvent.CycleCountCancelled(id, countType, at)
      (cancelled, event)

  /** A cycle count that is actively being executed. Count tasks are in progress.
    *
    * Transitions: [[complete]] -> [[Completed]], [[cancel]] -> [[Cancelled]].
    *
    * @param id
    *   unique cycle count identifier
    * @param warehouseAreaId
    *   the warehouse area being counted
    * @param skuIds
    *   the SKUs being counted
    * @param countType
    *   the type of cycle count
    * @param countMethod
    *   the counting method
    */
  case class InProgress(
      id: CycleCountId,
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod
  ) extends CycleCount:

    /** Completes the cycle count, transitioning from [[InProgress]] to [[Completed]].
      *
      * @param at
      *   instant of the completion
      * @return
      *   completed state and completion event
      */
    def complete(at: Instant): (Completed, CycleCountEvent.CycleCountCompleted) =
      val completed = Completed(id, warehouseAreaId, skuIds, countType, countMethod)
      val event = CycleCountEvent.CycleCountCompleted(id, countType, at)
      (completed, event)

    /** Cancels this in-progress cycle count.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, CycleCountEvent.CycleCountCancelled) =
      val cancelled = Cancelled(id, warehouseAreaId, skuIds, countType, countMethod)
      val event = CycleCountEvent.CycleCountCancelled(id, countType, at)
      (cancelled, event)

  /** A cycle count whose tasks have all been completed. Terminal state. */
  case class Completed(
      id: CycleCountId,
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod
  ) extends CycleCount

  /** A cycle count that was cancelled before completion. Terminal state. */
  case class Cancelled(
      id: CycleCountId,
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod
  ) extends CycleCount
