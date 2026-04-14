package neon.core

import neon.common.{ConsolidationGroupId, WaveId}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}

import scala.collection.mutable

class InMemoryConsolidationGroupRepository extends ConsolidationGroupRepository:
  val store: mutable.Map[ConsolidationGroupId, ConsolidationGroup] =
    mutable.Map.empty
  val events: mutable.ListBuffer[ConsolidationGroupEvent] =
    mutable.ListBuffer.empty
  def findById(id: ConsolidationGroupId): Option[ConsolidationGroup] =
    store.get(id)
  def findByWaveId(waveId: WaveId): List[ConsolidationGroup] =
    store.values.filter(_.waveId == waveId).toList
  def save(
      consolidationGroup: ConsolidationGroup,
      event: ConsolidationGroupEvent
  ): Unit =
    store(consolidationGroup.id) = consolidationGroup
    events += event
  def saveAll(
      entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]
  ): Unit =
    entries.foreach((consolidationGroup, event) => save(consolidationGroup, event))
