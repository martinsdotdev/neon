package neon.workstation

import neon.common.WorkstationId

import scala.concurrent.Future

/** Async port trait for [[Workstation]] aggregate persistence and queries. */
trait AsyncWorkstationRepository:
  def findById(id: WorkstationId): Future[Option[Workstation]]
  def findIdleByType(
      workstationType: WorkstationType
  ): Future[Option[Workstation.Idle]]
  def create(workstation: Workstation.Disabled): Future[Unit]
  def save(workstation: Workstation, event: WorkstationEvent): Future[Unit]
