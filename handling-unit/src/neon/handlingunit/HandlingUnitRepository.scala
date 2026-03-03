package neon.handlingunit

import neon.common.HandlingUnitId

/** Port trait for HandlingUnit aggregate persistence and queries. */
trait HandlingUnitRepository:
  def findById(id: HandlingUnitId): Option[HandlingUnit]
  def findByIds(ids: List[HandlingUnitId]): List[HandlingUnit]
  def save(handlingUnit: HandlingUnit, event: HandlingUnitEvent): Unit
