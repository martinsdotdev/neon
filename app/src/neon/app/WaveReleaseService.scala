package neon.app

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.wave.{Wave, WaveEvent, WavePlan, WaveRepository}

import java.time.Instant

case class WaveReleaseResult(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    tasks: List[(Task.Planned, TaskEvent.TaskCreated)],
    consolidationGroups: List[
      (ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)
    ]
)

class WaveReleaseService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  def release(wavePlan: WavePlan, at: Instant): WaveReleaseResult =
    waveRepository.save(wavePlan.wave, wavePlan.event)

    val tasks = TaskCreationPolicy(wavePlan.taskRequests, at)
    taskRepository.saveAll(tasks)

    val consolidationGroups = ConsolidationGroupFormationPolicy(wavePlan.event, at)
    consolidationGroupRepository.saveAll(consolidationGroups)

    WaveReleaseResult(
      wave = wavePlan.wave,
      event = wavePlan.event,
      tasks = tasks,
      consolidationGroups = consolidationGroups
    )
