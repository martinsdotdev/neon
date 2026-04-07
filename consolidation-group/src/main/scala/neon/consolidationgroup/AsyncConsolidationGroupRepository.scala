package neon.consolidationgroup

import neon.common.{ConsolidationGroupId, WaveId}

import scala.concurrent.Future

/** Async port trait for [[ConsolidationGroup]] aggregate persistence and queries. */
trait AsyncConsolidationGroupRepository:
  def findById(id: ConsolidationGroupId): Future[Option[ConsolidationGroup]]
  def findByWaveId(waveId: WaveId): Future[List[ConsolidationGroup]]
  def save(
      consolidationGroup: ConsolidationGroup,
      event: ConsolidationGroupEvent
  ): Future[Unit]

  /** Not transactional: individual entries may succeed or fail independently. */
  def saveAll(
      entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]
  ): Future[Unit]
