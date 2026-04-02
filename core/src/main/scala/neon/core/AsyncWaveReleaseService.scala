package neon.core

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
)(using ExecutionContext):

  def release(wavePlan: WavePlan, at: Instant): Future[WaveReleaseResult] =
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
