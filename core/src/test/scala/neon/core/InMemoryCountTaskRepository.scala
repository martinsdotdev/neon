package neon.core

import neon.common.{CountTaskId, CycleCountId}
import neon.counttask.{CountTask, CountTaskEvent, CountTaskRepository}

import scala.collection.mutable

class InMemoryCountTaskRepository extends CountTaskRepository:
  val store: mutable.Map[CountTaskId, CountTask] = mutable.Map.empty
  val events: mutable.ListBuffer[CountTaskEvent] = mutable.ListBuffer.empty
  def findById(id: CountTaskId): Option[CountTask] = store.get(id)
  def findByCycleCountId(cycleCountId: CycleCountId): List[CountTask] =
    store.values.filter(_.cycleCountId == cycleCountId).toList
  def save(countTask: CountTask, event: CountTaskEvent): Unit =
    store(countTask.id) = countTask
    events += event
  def saveAll(entries: List[(CountTask, CountTaskEvent)]): Unit =
    entries.foreach((countTask, event) => save(countTask, event))
