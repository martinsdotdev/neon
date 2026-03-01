package neon.consolidationgroup

import neon.common.{GroupId, OrderId, WaveId, WorkstationId}
import java.time.Instant

sealed trait ConsolidationGroup:
  def id: GroupId
  def waveId: WaveId
  def orderIds: List[OrderId]

object ConsolidationGroup:
  case class Created(
      id: GroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:
    def pick(at: Instant): (Picked, ConsolidationGroupEvent.ConsolidationGroupPicked) =
      val picked = Picked(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupPicked(id, waveId, at)
      (picked, event)

  case class Picked(
      id: GroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:
    def readyForWorkstation(
        at: Instant
    ): (ReadyForWorkstation, ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation) =
      val ready = ReadyForWorkstation(id, waveId, orderIds)
      val event = ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation(id, waveId, at)
      (ready, event)

  case class ReadyForWorkstation(
      id: GroupId,
      waveId: WaveId,
      orderIds: List[OrderId]
  ) extends ConsolidationGroup:
    def assign(
        workstationId: WorkstationId,
        at: Instant
    ): (Assigned, ConsolidationGroupEvent.ConsolidationGroupAssigned) =
      val assigned = Assigned(id, waveId, orderIds, workstationId)
      val event =
        ConsolidationGroupEvent.ConsolidationGroupAssigned(id, waveId, workstationId, at)
      (assigned, event)

  case class Assigned(
      id: GroupId,
      waveId: WaveId,
      orderIds: List[OrderId],
      workstationId: WorkstationId
  ) extends ConsolidationGroup:
    def complete(at: Instant): (Completed, ConsolidationGroupEvent.ConsolidationGroupCompleted) =
      val completed = Completed(id, waveId, orderIds, workstationId)
      val event =
        ConsolidationGroupEvent.ConsolidationGroupCompleted(id, waveId, workstationId, at)
      (completed, event)

  case class Completed(
      id: GroupId,
      waveId: WaveId,
      orderIds: List[OrderId],
      workstationId: WorkstationId
  ) extends ConsolidationGroup
