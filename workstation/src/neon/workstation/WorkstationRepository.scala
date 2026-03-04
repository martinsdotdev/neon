package neon.workstation

import neon.common.WorkstationId

/** Port trait for [[Workstation]] aggregate persistence and queries. */
trait WorkstationRepository:
  /** Finds a workstation by its unique identifier.
    *
    * @param id
    *   the workstation identifier
    * @return
    *   the workstation if it exists, [[None]] otherwise
    */
  def findById(id: WorkstationId): Option[Workstation]

  /** Finds an idle workstation of the given type, suitable for assignment.
    *
    * @param workstationType
    *   the desired workstation type
    * @return
    *   an idle workstation if one is available, [[None]] otherwise
    */
  def findIdleByType(workstationType: WorkstationType): Option[Workstation.Idle]

  /** Persists a workstation along with the event that caused the state change.
    *
    * @param workstation
    *   the workstation to persist
    * @param event
    *   the domain event to store
    */
  def save(workstation: Workstation, event: WorkstationEvent): Unit
