package neon.core

import neon.common.{ConsolidationGroupId, WaveId}
import neon.consolidationgroup.{
  AsyncConsolidationGroupRepository,
  ConsolidationGroup,
  ConsolidationGroupEvent
}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryAsyncConsolidationGroupRepository(recorder: CallRecorder = CallRecorder())
    extends AsyncConsolidationGroupRepository:
  val store: mutable.Map[ConsolidationGroupId, ConsolidationGroup] = mutable.Map.empty
  val events: mutable.ListBuffer[ConsolidationGroupEvent] = mutable.ListBuffer.empty

  def findById(id: ConsolidationGroupId): Future[Option[ConsolidationGroup]] =
    recorder.record("consolidationGroup.findById")
    Future.successful(store.get(id))

  def findByWaveId(waveId: WaveId): Future[List[ConsolidationGroup]] =
    recorder.record("consolidationGroup.findByWaveId")
    Future.successful(store.values.filter(_.waveId == waveId).toList)

  def save(
      consolidationGroup: ConsolidationGroup,
      event: ConsolidationGroupEvent
  ): Future[Unit] =
    recorder.record("consolidationGroup.save")
    store(consolidationGroup.id) = consolidationGroup
    events += event
    Future.unit

  def saveAll(entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]): Future[Unit] =
    entries.foreach { (consolidationGroup, event) =>
      recorder.record("consolidationGroup.save")
      store(consolidationGroup.id) = consolidationGroup
      events += event
    }
    Future.unit
