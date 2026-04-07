package neon.slot

import neon.common.{SlotId, WorkstationId}

import scala.concurrent.Future

/** Async port trait for [[Slot]] aggregate persistence and queries. */
trait AsyncSlotRepository:
  def findById(id: SlotId): Future[Option[Slot]]
  def findByWorkstationId(workstationId: WorkstationId): Future[List[Slot]]
  def save(slot: Slot, event: SlotEvent): Future[Unit]

  /** Not transactional: individual entries may succeed or fail independently. */
  def saveAll(entries: List[(Slot, SlotEvent)]): Future[Unit]
