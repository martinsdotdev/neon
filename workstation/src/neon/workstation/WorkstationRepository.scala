package neon.workstation

import neon.common.WorkstationId

/** Port trait for Workstation aggregate persistence and queries. */
trait WorkstationRepository:
  def findById(id: WorkstationId): Option[Workstation]
  def findIdleByType(workstationType: WorkstationType): Option[Workstation.Idle]
  def save(workstation: Workstation, event: WorkstationEvent): Unit
