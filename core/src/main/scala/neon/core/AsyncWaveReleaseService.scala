package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.consolidationgroup.AsyncConsolidationGroupRepository
import neon.task.AsyncTaskRepository
import neon.wave.{AsyncWaveRepository, WavePlan}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[WaveReleaseService]]. Releases a wave plan, persisting the wave, creating
  * tasks from its task requests, and forming consolidation groups from its order grouping.
  */
class AsyncWaveReleaseService(
    waveRepository: AsyncWaveRepository,
    taskRepository: AsyncTaskRepository,
    consolidationGroupRepository: AsyncConsolidationGroupRepository
)(using ExecutionContext)
    extends LazyLogging:

  def release(wavePlan: WavePlan, at: Instant): Future[WaveReleaseResult] =
    logger.debug(
      "Releasing wave {}",
      wavePlan.wave.id.value
    )
    for
      _ <- waveRepository.save(wavePlan.wave, wavePlan.event)
      tasks = TaskCreationPolicy(wavePlan.taskRequests, at)
      _ <- taskRepository.saveAll(tasks)
      consolidationGroups = ConsolidationGroupFormationPolicy(wavePlan.event, at)
      _ <- consolidationGroupRepository.saveAll(consolidationGroups)
    yield WaveReleaseResult(
      wave = wavePlan.wave,
      event = wavePlan.event,
      tasks = tasks,
      consolidationGroups = consolidationGroups
    )
