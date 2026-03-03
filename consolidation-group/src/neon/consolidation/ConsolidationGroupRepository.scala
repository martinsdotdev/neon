package neon.consolidationgroup

import neon.common.{GroupId, WaveId}

/** Port trait for ConsolidationGroup aggregate persistence and queries. */
trait ConsolidationGroupRepository:
  def findById(id: GroupId): Option[ConsolidationGroup]
  def findByWaveId(waveId: WaveId): List[ConsolidationGroup]
  def save(consolidationGroup: ConsolidationGroup, event: ConsolidationGroupEvent): Unit
  def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit
