package neon.task

import neon.common.{HandlingUnitId, TaskId, WaveId}

/** Port trait for Task aggregate persistence and queries. */
trait TaskRepository:
  def findById(id: TaskId): Option[Task]
  def findByWaveId(waveId: WaveId): List[Task]
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task]
  def save(task: Task, event: TaskEvent): Unit
  def saveAll(entries: List[(Task, TaskEvent)]): Unit
