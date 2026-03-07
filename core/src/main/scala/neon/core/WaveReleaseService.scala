package neon.core

import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.wave.{Wave, WaveEvent, WavePlan, WaveRepository}

import java.time.Instant

/** The result of a successful wave release, containing the released wave, the created tasks, and
  * any consolidation groups formed.
  *
  * @param wave
  *   the released wave
  * @param event
  *   the wave released event
  * @param tasks
  *   planned tasks created from the wave's task requests
  * @param consolidationGroups
  *   consolidation groups formed from the wave's order grouping, empty if none apply
  */
case class WaveReleaseResult(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    tasks: List[(Task.Planned, TaskEvent.TaskCreated)],
    consolidationGroups: List[
      (ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)
    ]
)

/** Releases a wave plan, persisting the wave, creating tasks from its task requests, and forming
  * consolidation groups from its order grouping.
  *
  * @param waveRepository
  *   repository for wave persistence
  * @param taskRepository
  *   repository for task persistence
  * @param consolidationGroupRepository
  *   repository for consolidation group persistence
  */
class WaveReleaseService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository
):
  /** Releases a [[WavePlan]], creating tasks and consolidation groups.
    *
    * Steps: (1) persist the released wave, (2) create planned tasks from task requests via
    * [[TaskCreationPolicy]], (3) form consolidation groups via
    * [[ConsolidationGroupFormationPolicy]].
    *
    * @param wavePlan
    *   the wave plan containing the released wave and its task requests
    * @param at
    *   instant of the release
    * @return
    *   the release result with all created entities
    */
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
