package neon.consolidationgroup

import neon.common.{ConsolidationGroupId, WaveId}

/** Port trait for [[ConsolidationGroup]] aggregate persistence and queries. */
trait ConsolidationGroupRepository:

  /** Finds a consolidation group by its unique identifier.
    *
    * @param id
    *   the group identifier
    * @return
    *   the group if it exists, [[None]] otherwise
    */
  def findById(id: ConsolidationGroupId): Option[ConsolidationGroup]

  /** Finds all consolidation groups belonging to a wave.
    *
    * @param waveId
    *   the wave identifier
    * @return
    *   all groups for the given wave, or an empty list if none exist
    */
  def findByWaveId(waveId: WaveId): List[ConsolidationGroup]

  /** Persists a consolidation group together with its domain event.
    *
    * @param consolidationGroup
    *   the group state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(consolidationGroup: ConsolidationGroup, event: ConsolidationGroupEvent): Unit

  /** Persists multiple consolidation groups and their events atomically.
    *
    * @param entries
    *   pairs of group state and corresponding event
    */
  def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Unit
