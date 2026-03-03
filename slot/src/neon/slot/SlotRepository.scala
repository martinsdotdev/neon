package neon.slot

import neon.common.{SlotId, WorkstationId}

/** Port trait for Slot aggregate persistence and queries. */
trait SlotRepository:
  def findById(id: SlotId): Option[Slot]
  def findByWorkstationId(workstationId: WorkstationId): List[Slot]
  def save(slot: Slot, event: SlotEvent): Unit
  def saveAll(entries: List[(Slot, SlotEvent)]): Unit
