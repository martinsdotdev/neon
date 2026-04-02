package neon.core

import neon.common.{LocationId, WaveId}

import scala.concurrent.Future

/** Async port trait for wave dispatch dock/carrier mappings used during wave planning.
  */
trait AsyncWaveDispatchAssignmentRepository:
  def findActiveByDock(
      dockId: LocationId
  ): Future[List[ActiveDockCarrierAssignment]]

  def reserveForWave(
      waveId: WaveId,
      assignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules
  ): Future[Either[WavePlanningError.DockConflict, Unit]]
