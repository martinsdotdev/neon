package neon.core

import neon.common.HandlingUnitId
import neon.handlingunit.{HandlingUnit, HandlingUnitEvent, HandlingUnitRepository}

import scala.collection.mutable

class InMemoryHandlingUnitRepository extends HandlingUnitRepository:
  val store: mutable.Map[HandlingUnitId, HandlingUnit] = mutable.Map.empty
  val events: mutable.ListBuffer[HandlingUnitEvent] = mutable.ListBuffer.empty
  def findById(id: HandlingUnitId): Option[HandlingUnit] = store.get(id)
  def findByIds(ids: List[HandlingUnitId]): List[HandlingUnit] =
    ids.flatMap(store.get)
  def save(handlingUnit: HandlingUnit, event: HandlingUnitEvent): Unit =
    store(handlingUnit.id) = handlingUnit
    events += event
