package neon.core

import neon.common.{HandlingUnitId, TaskId, UserId, WaveId}
import neon.task.{AsyncTaskRepository, Task, TaskEvent}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryAsyncTaskRepository(recorder: CallRecorder = CallRecorder())
    extends AsyncTaskRepository:
  val store: mutable.Map[TaskId, Task] = mutable.Map.empty
  val events: mutable.ListBuffer[TaskEvent] = mutable.ListBuffer.empty

  def findById(id: TaskId): Future[Option[Task]] =
    recorder.record("task.findById")
    Future.successful(store.get(id))

  def findByWaveId(waveId: WaveId): Future[List[Task]] =
    recorder.record("task.findByWaveId")
    Future.successful(store.values.filter(_.waveId.contains(waveId)).toList)

  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[Task]] =
    recorder.record("task.findByHandlingUnitId")
    Future.successful(store.values.filter(_.handlingUnitId.contains(handlingUnitId)).toList)

  def findAssignedTo(userId: UserId, state: Option[String] = None): Future[List[Task]] =
    recorder.record("task.findAssignedTo")
    val assigned = store.values.toList.filter {
      case task: Task.Assigned  => task.assignedTo == userId
      case task: Task.Completed => task.assignedTo == userId
      case task: Task.Cancelled => task.assignedTo.contains(userId)
      case _                    => false
    }
    Future.successful(
      state.fold(assigned)(stateName => assigned.filter(_.getClass.getSimpleName == stateName))
    )

  def save(task: Task, event: TaskEvent): Future[Unit] =
    recorder.record("task.save")
    store(task.id) = task
    events += event
    Future.unit

  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit] =
    entries.foreach { (task, event) =>
      recorder.record("task.save")
      store(task.id) = task
      events += event
    }
    Future.unit
