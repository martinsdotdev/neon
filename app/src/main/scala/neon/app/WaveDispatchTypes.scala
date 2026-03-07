package neon.app

import neon.common.{CarrierId, LocationId, WaveId}

/** Carrier assignment to a shipping dock for the wave being planned.
  */
case class DockCarrierAssignment(dockId: LocationId, carrierId: CarrierId)

/** Existing active wave assignment for conflict checks.
  */
case class ActiveDockCarrierAssignment(
    waveId: WaveId,
    dockId: LocationId,
    carrierId: CarrierId
)

/** Rules for dock/carrier validation during wave planning.
  *
  * @param enforceDockCarrierExclusivityAcrossActiveWaves
  *   if true, a dock in use by one carrier cannot be assigned to a different
  *   carrier in another active wave
  * @param allowCarrierMultipleDocksWithinWave
  *   if false, the same carrier cannot appear in more than one dock assignment
  *   within the same wave
  */
case class WaveDispatchRules(
    enforceDockCarrierExclusivityAcrossActiveWaves: Boolean = true,
    allowCarrierMultipleDocksWithinWave: Boolean = false
)
