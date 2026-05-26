package neon.task

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  StockPositionId,
  TaskId,
  UserId,
  WaveId
}

import java.time.Instant

/** Warehouse task aggregate with typestate-encoded lifecycle.
  *
  * A task represents a single atomic warehouse operation (pick, putaway, replenish, or transfer)
  * for one SKU. The state machine is [[Planned]] → [[Allocated]] → [[Assigned]] → [[Completed]],
  * with [[Cancelled]] reachable from any non-terminal state. Transitions are enforced at compile
  * time — only valid source states expose the corresponding method.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[Task.Planned], name = "Planned"),
    new JsonSubTypes.Type(value = classOf[Task.Allocated], name = "Allocated"),
    new JsonSubTypes.Type(value = classOf[Task.Assigned], name = "Assigned"),
    new JsonSubTypes.Type(value = classOf[Task.Completed], name = "Completed"),
    new JsonSubTypes.Type(value = classOf[Task.Cancelled], name = "Cancelled")
  )
)
sealed trait Task:

  /** The unique identifier of this task. */
  def id: TaskId

  /** The type of warehouse operation this task represents. */
  def taskType: TaskType

  /** The SKU targeted by this task. */
  def skuId: SkuId

  /** The order this task fulfills. */
  def orderId: OrderId

  /** The wave that originated this task, if any. */
  def waveId: Option[WaveId]

  /** The handling unit associated with this task, if any. */
  def handlingUnitId: Option[HandlingUnitId]

  /** The stock position allocated for this task, if any. */
  def stockPositionId: Option[StockPositionId]

/** Factory and state definitions for the [[Task]] aggregate. */
object Task:

  /** Creates a new task in the [[Planned]] state from the given parameters.
    *
    * @param taskType
    *   the warehouse operation type
    * @param skuId
    *   the SKU to be handled
    * @param packagingLevel
    *   the packaging level of the SKU
    * @param requestedQuantity
    *   quantity to handle (must be positive)
    * @param orderId
    *   the order this task fulfills
    * @param waveId
    *   the originating wave, if any
    * @param parentTaskId
    *   the parent task (set for shortpick replacements)
    * @param handlingUnitId
    *   the associated handling unit, if any
    * @param at
    *   instant of the creation
    * @return
    *   planned state and creation event
    */
  def create(
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQuantity: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      at: Instant,
      stockPositionId: Option[StockPositionId] = None
  ): (Planned, TaskEvent.TaskCreated) =
    require(requestedQuantity > 0, s"requestedQuantity must be positive, got $requestedQuantity")
    val id = TaskId()
    val planned =
      Planned(
        id = id,
        taskType = taskType,
        skuId = skuId,
        packagingLevel = packagingLevel,
        requestedQuantity = requestedQuantity,
        orderId = orderId,
        waveId = waveId,
        parentTaskId = parentTaskId,
        handlingUnitId = handlingUnitId,
        stockPositionId = stockPositionId
      )
    val event =
      TaskEvent.TaskCreated(
        taskId = id,
        taskType = taskType,
        skuId = skuId,
        packagingLevel = packagingLevel,
        orderId = orderId,
        waveId = waveId,
        parentTaskId = parentTaskId,
        handlingUnitId = handlingUnitId,
        requestedQuantity = requestedQuantity,
        occurredAt = at,
        stockPositionId = stockPositionId
      )
    (planned, event)

  /** A task that has been created but not yet assigned locations.
    *
    * Transitions: [[allocate]] → [[Allocated]], [[cancel]] → [[Cancelled]].
    */
  case class Planned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQuantity: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      stockPositionId: Option[StockPositionId] = None
  ) extends Task:

    /** Assigns source and destination locations, transitioning to [[Allocated]].
      *
      * @param sourceLocationId
      *   pick location
      * @param destinationLocationId
      *   target location for the picked item
      * @param at
      *   instant of the allocation
      * @return
      *   allocated state and allocation event
      */
    def allocate(
        sourceLocationId: LocationId,
        destinationLocationId: LocationId,
        at: Instant
    ): (Allocated, TaskEvent.TaskAllocated) =
      val allocated =
        Allocated(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          requestedQuantity = requestedQuantity,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = sourceLocationId,
          destinationLocationId = destinationLocationId
        )
      val event = TaskEvent.TaskAllocated(
        taskId = id,
        taskType = taskType,
        sourceLocationId = sourceLocationId,
        destinationLocationId = destinationLocationId,
        occurredAt = at
      )
      (allocated, event)

    /** Cancels this planned task before locations are assigned.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled =
        Cancelled(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = None,
          destinationLocationId = None,
          assignedTo = None
        )
      val event =
        TaskEvent.TaskCancelled(
          taskId = id,
          taskType = taskType,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          sourceLocationId = None,
          destinationLocationId = None,
          assignedTo = None,
          occurredAt = at
        )
      (cancelled, event)

  /** A task with source and destination locations assigned, ready for user assignment.
    *
    * Transitions: [[assign]] → [[Assigned]], [[cancel]] → [[Cancelled]].
    */
  case class Allocated(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQuantity: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      stockPositionId: Option[StockPositionId] = None,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId
  ) extends Task:

    /** Assigns the task to a user, transitioning to [[Assigned]].
      *
      * @param userId
      *   the user who will execute this task
      * @param at
      *   instant of the assignment
      * @return
      *   assigned state and assignment event
      */
    def assign(userId: UserId, at: Instant): (Assigned, TaskEvent.TaskAssigned) =
      val assigned =
        Assigned(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          requestedQuantity = requestedQuantity,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = sourceLocationId,
          destinationLocationId = destinationLocationId,
          assignedTo = userId
        )
      val event = TaskEvent.TaskAssigned(id, taskType, userId, at)
      (assigned, event)

    /** Cancels this allocated task before a user is assigned.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled =
        Cancelled(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = Some(sourceLocationId),
          destinationLocationId = Some(destinationLocationId),
          assignedTo = None
        )
      val event = TaskEvent.TaskCancelled(
        taskId = id,
        taskType = taskType,
        waveId = waveId,
        parentTaskId = parentTaskId,
        handlingUnitId = handlingUnitId,
        sourceLocationId = Some(sourceLocationId),
        destinationLocationId = Some(destinationLocationId),
        assignedTo = None,
        occurredAt = at
      )
      (cancelled, event)

  /** A task assigned to a user and ready for execution.
    *
    * Transitions: [[complete]] → [[Completed]], [[cancel]] → [[Cancelled]].
    */
  case class Assigned(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQuantity: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      stockPositionId: Option[StockPositionId] = None,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      assignedTo: UserId
  ) extends Task:

    /** Completes the task with the actual quantity handled.
      *
      * When `actualQuantity` is less than `requestedQuantity`, downstream policies (e.g.
      * ShortpickPolicy) create a replacement task for the remainder. A zero `actualQuantity` is
      * valid (full shortpick).
      *
      * @param actualQuantity
      *   the quantity actually handled (must be non-negative)
      * @param at
      *   instant of the completion
      * @return
      *   completed state and completion event
      */
    def complete(actualQuantity: Int, at: Instant): (Completed, TaskEvent.TaskCompleted) =
      require(actualQuantity >= 0, s"actualQuantity must be non-negative, got $actualQuantity")
      val completed =
        Completed(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          requestedQuantity = requestedQuantity,
          actualQuantity = actualQuantity,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = sourceLocationId,
          destinationLocationId = destinationLocationId,
          assignedTo = assignedTo
        )
      val event =
        TaskEvent.TaskCompleted(
          taskId = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          sourceLocationId = sourceLocationId,
          destinationLocationId = destinationLocationId,
          requestedQuantity = requestedQuantity,
          actualQuantity = actualQuantity,
          assignedTo = assignedTo,
          occurredAt = at
        )
      (completed, event)

    /** Cancels this assigned task, preserving location and user context.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, TaskEvent.TaskCancelled) =
      val cancelled =
        Cancelled(
          id = id,
          taskType = taskType,
          skuId = skuId,
          packagingLevel = packagingLevel,
          orderId = orderId,
          waveId = waveId,
          parentTaskId = parentTaskId,
          handlingUnitId = handlingUnitId,
          stockPositionId = stockPositionId,
          sourceLocationId = Some(sourceLocationId),
          destinationLocationId = Some(destinationLocationId),
          assignedTo = Some(assignedTo)
        )
      val event = TaskEvent.TaskCancelled(
        taskId = id,
        taskType = taskType,
        waveId = waveId,
        parentTaskId = parentTaskId,
        handlingUnitId = handlingUnitId,
        sourceLocationId = Some(sourceLocationId),
        destinationLocationId = Some(destinationLocationId),
        assignedTo = Some(assignedTo),
        occurredAt = at
      )
      (cancelled, event)

  /** A task that has been successfully executed. Terminal state -- no further transitions. */
  case class Completed(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      requestedQuantity: Int,
      actualQuantity: Int,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      stockPositionId: Option[StockPositionId] = None,
      sourceLocationId: LocationId,
      destinationLocationId: LocationId,
      assignedTo: UserId
  ) extends Task

  /** A task cancelled from any non-terminal state. Terminal state -- no further transitions.
    *
    * Location and user fields are optional because cancellation may occur before allocation (from
    * [[Planned]]) or before assignment (from [[Allocated]]).
    */
  case class Cancelled(
      id: TaskId,
      taskType: TaskType,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      orderId: OrderId,
      waveId: Option[WaveId],
      parentTaskId: Option[TaskId],
      handlingUnitId: Option[HandlingUnitId],
      stockPositionId: Option[StockPositionId] = None,
      sourceLocationId: Option[LocationId],
      destinationLocationId: Option[LocationId],
      assignedTo: Option[UserId]
  ) extends Task
