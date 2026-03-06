package neon.app

import neon.common.{LocationId, WaveId}

/** Port trait for wave dispatch dock/carrier mappings used during wave planning.
  */
trait WaveDispatchAssignmentRepository:

  /** Returns active wave assignments for a dock.
    *
    * Implementations should only return assignments for active waves
    * (i.e. released waves).
    */
  def findActiveByDock(dockId: LocationId): List[ActiveDockCarrierAssignment]

  /** Reserves dock/carrier assignments for a wave atomically.
    *
    * The check and persistence must happen in a single transaction to avoid race
    * conditions across concurrent wave releases.
    */
  def reserveForWave(
      waveId: WaveId,
      assignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules
  ): Either[WavePlanningError.DockConflict, Unit]
