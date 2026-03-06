package neon.consolidationgroup

import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}

import java.time.Instant

/** Typestate-encoded aggregate for consolidation group lifecycle management.
  *
  * A consolidation group batches orders from a wave for workstation processing. The state machine
  * follows: [[ConsolidationGroup.Created]] -> [[ConsolidationGroup.Picked]] ->
  * [[ConsolidationGroup.ReadyForWorkstation]] -> [[ConsolidationGroup.Assigned]] ->
  * [[ConsolidationGroup.Completed]], with [[ConsolidationGroup.Cancelled]] reachable from any
  * non-terminal state. Transitions are only available on valid source states, enforced at compile
  * time.
  */
sealed trait ConsolidationGroup:
  /** The unique identifier of this consolidation group. */
  def id: ConsolidationGroupId

  /** The wave that originated this consolidation group. */
  def waveId: WaveId

  /** The orders grouped in this consolidation group. */
  def orderIds: List[OrderId]

/** Factory and state definitions for the [[ConsolidationGroup]] aggregate. */
object ConsolidationGroup:

  /** Creates a new consolidation group for a set of orders within a wave.
    *
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders to include (must be non-empty)
    * @param at
    *   instant of creation
    * @return
    *   created state and creation event
    */
  def create(
      waveId: WaveId,
      orderIds: List[OrderId],
      at: Instant
  ): (Created, ConsolidationGroupEvent.ConsolidationGroupCreated) =
    require(orderIds.nonEmpty, "orderIds must not be empty")
    val id = ConsolidationGroupId()
    val created = Created(id, waveId, orderIds)
    val event = ConsolidationGroupEvent.ConsolidationGroupCreated(id, waveId, orderIds, at)
    (created, event)

  /** A newly created consolidation group awaiting picking completion.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    */
  case class Created(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:

    /** Marks all picks complete, transitioning from [[Created]] to [[Picked]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   picked state and picking completed event
      */
    def pick(at: Instant): (Picked, ConsolidationGroupEvent.ConsolidationGroupPicked) =
      val picked = Picked(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupPicked(id, waveId, at)
      (picked, event)

    /** Cancels this created consolidation group.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled) =
      val cancelled = Cancelled(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupCancelled(id, waveId, at)
      (cancelled, event)

  /** A consolidation group whose picks are complete, awaiting buffer arrival.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    */
  case class Picked(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:

    /** Signals that all buffered units have arrived, transitioning from [[Picked]] to
      * [[ReadyForWorkstation]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   ready state and readiness event
      */
    def readyForWorkstation(
        at: Instant
    ): (ReadyForWorkstation, ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation) =
      val ready = ReadyForWorkstation(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation(id, waveId, at)
      (ready, event)

    /** Cancels this picked consolidation group.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled) =
      val cancelled = Cancelled(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupCancelled(id, waveId, at)
      (cancelled, event)

  /** A consolidation group ready to be assigned to a workstation.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    */
  case class ReadyForWorkstation(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:

    /** Assigns this group to a workstation, transitioning from [[ReadyForWorkstation]] to
      * [[Assigned]].
      *
      * @param workstationId
      *   the workstation to assign this group to
      * @param at
      *   instant of the assignment
      * @return
      *   assigned state and assignment event
      */
    def assign(
        workstationId: WorkstationId,
        at: Instant
    ): (Assigned, ConsolidationGroupEvent.ConsolidationGroupAssigned) =
      val assigned = Assigned(id, waveId, orderIds, workstationId)
      val event =
        ConsolidationGroupEvent.ConsolidationGroupAssigned(id, waveId, workstationId, at)
      (assigned, event)

    /** Cancels this ready-for-workstation consolidation group.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled) =
      val cancelled = Cancelled(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupCancelled(id, waveId, at)
      (cancelled, event)

  /** A consolidation group assigned to a workstation for deconsolidation.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    * @param workstationId
    *   the workstation processing this group
    */
  case class Assigned(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId],
      workstationId: WorkstationId
  ) extends ConsolidationGroup:

    /** Completes workstation processing, transitioning from [[Assigned]] to [[Completed]].
      *
      * @param at
      *   instant of the completion
      * @return
      *   completed state and completion event
      */
    def complete(at: Instant): (Completed, ConsolidationGroupEvent.ConsolidationGroupCompleted) =
      val completed = Completed(id, waveId, orderIds, workstationId)
      val event =
        ConsolidationGroupEvent.ConsolidationGroupCompleted(id, waveId, workstationId, at)
      (completed, event)

    /** Cancels this assigned consolidation group.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, ConsolidationGroupEvent.ConsolidationGroupCancelled) =
      val cancelled = Cancelled(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupCancelled(id, waveId, at)
      (cancelled, event)

  /** A consolidation group that has been fully processed. Terminal state.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    * @param workstationId
    *   the workstation that processed this group
    */
  case class Completed(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId],
      workstationId: WorkstationId
  ) extends ConsolidationGroup

  /** A consolidation group that was cancelled before completion. Terminal state.
    *
    * @param id
    *   unique group identifier
    * @param waveId
    *   the wave that originated this group
    * @param orderIds
    *   orders included in this group
    */
  case class Cancelled(
      id: ConsolidationGroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup
