package neon.task

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{HandlingUnitId, R2dbcProjectionQueries, TaskId, UserId, WaveId}
import neon.task.TaskProjectionSchema.{TaskByAssignee, TaskByHandlingUnit, TaskByWave}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoTaskRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[TaskActor.Command, Task](
      actorSystem = actorSystem,
      entityKey = TaskActor.EntityKey,
      behaviorFactory = TaskActor.apply,
      getState = TaskActor.GetState.apply
    )
    with AsyncTaskRepository
    with R2dbcProjectionQueries:

  def findById(id: TaskId): Future[Option[Task]] =
    findByEntityId(id.value.toString)

  def findByWaveId(waveId: WaveId): Future[List[Task]] =
    queryProjectionIds(
      sql = TaskByWave.SelectTaskIdsByWaveId,
      param = waveId.value,
      idColumn = TaskByWave.TaskId
    )
      .flatMap(ids => Future.sequence(ids.map(id => findById(TaskId(id)))).map(_.flatten))

  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[Task]] =
    queryProjectionIds(
      sql = TaskByHandlingUnit.SelectTaskIdsByHandlingUnitId,
      param = handlingUnitId.value,
      idColumn = TaskByHandlingUnit.TaskId
    ).flatMap(ids => Future.sequence(ids.map(id => findById(TaskId(id)))).map(_.flatten))

  def findAssignedTo(
      userId: UserId,
      state: Option[String] = None
  ): Future[List[Task]] =
    val ids = state match
      case Some(s) =>
        queryProjectionIds(
          sql = TaskByAssignee.SelectTaskIdsByUserIdAndState,
          params = List(userId.value, s),
          idColumn = TaskByAssignee.TaskId
        )
      case None =>
        queryProjectionIds(
          sql = TaskByAssignee.SelectTaskIdsByUserId,
          param = userId.value,
          idColumn = TaskByAssignee.TaskId
        )
    ids.flatMap(taskIds => Future.sequence(taskIds.map(id => findById(TaskId(id)))).map(_.flatten))

  def save(task: Task, event: TaskEvent): Future[Unit] =
    val ref = entityRef(task.id.value.toString)
    event match
      case e: TaskEvent.TaskCreated =>
        ref
          .askWithStatus(TaskActor.Create(task.asInstanceOf[Task.Planned], e, _))
          .map(_ => ())
      case e: TaskEvent.TaskAllocated =>
        ref
          .askWithStatus[TaskActor.AllocateResponse](
            TaskActor.Allocate(e.sourceLocationId, e.destinationLocationId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskAssigned =>
        ref
          .askWithStatus[TaskActor.AssignResponse](TaskActor.Assign(e.userId, e.occurredAt, _))
          .map(_ => ())
      case e: TaskEvent.TaskCompleted =>
        ref
          .askWithStatus[TaskActor.CompleteResponse](
            TaskActor.Complete(e.actualQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskCancelled =>
        ref
          .askWithStatus[TaskActor.CancelResponse](TaskActor.Cancel(e.occurredAt, _))
          .map(_ => ())

  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit] =
    sequenceSaves(entries)(save)
