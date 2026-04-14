package neon.core

import neon.common.{HandlingUnitId, TaskId, WaveId}
import neon.task.{Task, TaskEvent, TaskRepository}

import scala.collection.mutable

class InMemoryTaskRepository extends TaskRepository:
  val store: mutable.Map[TaskId, Task] = mutable.Map.empty
  val events: mutable.ListBuffer[TaskEvent] = mutable.ListBuffer.empty
  def findById(id: TaskId): Option[Task] = store.get(id)
  def findByWaveId(waveId: WaveId): List[Task] =
    store.values.filter(_.waveId.contains(waveId)).toList
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[Task] =
    store.values.filter(_.handlingUnitId.contains(handlingUnitId)).toList
  def save(task: Task, event: TaskEvent): Unit =
    store(task.id) = task
    events += event
  def saveAll(entries: List[(Task, TaskEvent)]): Unit =
    entries.foreach((task, event) => save(task, event))
