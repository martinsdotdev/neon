package neon.handlingunit

import neon.common.HandlingUnitId

/** Port trait for [[HandlingUnit]] aggregate persistence and queries. */
trait HandlingUnitRepository:

  /** Finds a handling unit by its unique identifier.
    *
    * @param id
    *   the handling unit identifier
    * @return
    *   the handling unit if it exists, [[None]] otherwise
    */
  def findById(id: HandlingUnitId): Option[HandlingUnit]

  /** Finds multiple handling units by their identifiers.
    *
    * @param ids
    *   the handling unit identifiers to look up
    * @return
    *   the matching handling units, or an empty list if none match
    */
  def findByIds(ids: List[HandlingUnitId]): List[HandlingUnit]

  /** Persists a handling unit together with its domain event.
    *
    * @param handlingUnit
    *   the handling unit state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(handlingUnit: HandlingUnit, event: HandlingUnitEvent): Unit
