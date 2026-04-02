package neon.handlingunit

import neon.common.HandlingUnitId

import scala.concurrent.Future

/** Async port trait for [[HandlingUnit]] aggregate persistence and queries. */
trait AsyncHandlingUnitRepository:
  def findById(id: HandlingUnitId): Future[Option[HandlingUnit]]
  def findByIds(ids: List[HandlingUnitId]): Future[List[HandlingUnit]]
  def save(handlingUnit: HandlingUnit, event: HandlingUnitEvent): Future[Unit]
