package neon.task

import neon.common.{HandlingUnitId, R2dbcProjectionQueries, TaskId, WaveId}
import io.r2dbc.spi.ConnectionFactory
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
      "SELECT task_id FROM task_by_wave WHERE wave_id = $1",
      waveId.value,
      "task_id"
    )
      .flatMap(ids => Future.sequence(ids.map(id => findById(TaskId(id)))).map(_.flatten))

  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[Task]] =
    queryProjectionIds(
      "SELECT task_id FROM task_by_handling_unit WHERE handling_unit_id = $1",
      handlingUnitId.value,
      "task_id"
    ).flatMap(ids => Future.sequence(ids.map(id => findById(TaskId(id)))).map(_.flatten))

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
