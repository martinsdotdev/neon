package neon.task

import io.r2dbc.spi.ConnectionFactory
import neon.common.{HandlingUnitId, R2dbcProjectionQueries, TaskId, UserId, WaveId}
import neon.task.TaskProjectionSchema.{TaskByAssignee, TaskByHandlingUnit, TaskByWave}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class PekkoTaskRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncTaskRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(TaskActor.EntityKey)(ctx => TaskActor(ctx.entityId)))

  def findById(id: TaskId): Future[Option[Task]] =
    sharding.entityRefFor(TaskActor.EntityKey, id.value.toString).ask(TaskActor.GetState(_))

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
    val entityRef = sharding.entityRefFor(TaskActor.EntityKey, task.id.value.toString)
    event match
      case e: TaskEvent.TaskCreated =>
        entityRef
          .askWithStatus(TaskActor.Create(task.asInstanceOf[Task.Planned], e, _))
          .map(_ => ())
      case e: TaskEvent.TaskAllocated =>
        entityRef
          .askWithStatus[TaskActor.AllocateResponse](
            TaskActor.Allocate(e.sourceLocationId, e.destinationLocationId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskAssigned =>
        entityRef
          .askWithStatus[TaskActor.AssignResponse](TaskActor.Assign(e.userId, e.occurredAt, _))
          .map(_ => ())
      case e: TaskEvent.TaskCompleted =>
        entityRef
          .askWithStatus[TaskActor.CompleteResponse](
            TaskActor.Complete(e.actualQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskCancelled =>
        entityRef
          .askWithStatus[TaskActor.CancelResponse](TaskActor.Cancel(e.occurredAt, _))
          .map(_ => ())

  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit] =
    Future.sequence(entries.map((task, event) => save(task, event))).map(_ => ())
