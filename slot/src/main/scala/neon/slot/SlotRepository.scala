package neon.slot

import neon.common.{SlotId, WorkstationId}

/** Port trait for [[Slot]] aggregate persistence and queries. */
trait SlotRepository:
  /** Finds a slot by its unique identifier.
    *
    * @param id
    *   the slot identifier
    * @return
    *   the slot if it exists, [[None]] otherwise
    */
  def findById(id: SlotId): Option[Slot]

  /** Finds all slots belonging to a workstation.
    *
    * @param workstationId
    *   the workstation identifier
    * @return
    *   all slots at the given workstation, in any state
    */
  def findByWorkstationId(workstationId: WorkstationId): List[Slot]

  /** Persists a slot along with the event that caused the state change.
    *
    * @param slot
    *   the slot to persist
    * @param event
    *   the domain event to store
    */
  def save(slot: Slot, event: SlotEvent): Unit

  /** Persists multiple slots with their associated events atomically.
    *
    * @param entries
    *   pairs of slot and corresponding event
    */
  def saveAll(entries: List[(Slot, SlotEvent)]): Unit
