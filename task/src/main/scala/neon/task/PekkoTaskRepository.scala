package neon.task

import neon.common.{HandlingUnitId, TaskId, WaveId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncTaskRepository]]. Single-entity operations route to the
  * TaskActor via Cluster Sharding. Cross-entity queries will be served by read-side projection
  * tables.
  */
class PekkoTaskRepository(system: ActorSystem[?])(using Timeout) extends AsyncTaskRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(TaskActor.EntityKey)(ctx => TaskActor(ctx.entityId))
  )

  def findById(id: TaskId): Future[Option[Task]] =
    sharding
      .entityRefFor(TaskActor.EntityKey, id.value.toString)
      .ask(TaskActor.GetState(_))

  def findByWaveId(waveId: WaveId): Future[List[Task]] =
    // TODO: query task_by_wave projection table via R2DBC
    Future.successful(Nil)

  def findByHandlingUnitId(
      handlingUnitId: HandlingUnitId
  ): Future[List[Task]] =
    // TODO: query task_by_handling_unit projection table via R2DBC
    Future.successful(Nil)

  def save(task: Task, event: TaskEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      TaskActor.EntityKey,
      task.id.value.toString
    )
    event match
      case e: TaskEvent.TaskCreated =>
        entityRef
          .askWithStatus(
            TaskActor.Create(task.asInstanceOf[Task.Planned], e, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskAllocated =>
        entityRef
          .askWithStatus[TaskActor.AllocateResponse](
            TaskActor.Allocate(
              e.sourceLocationId,
              e.destinationLocationId,
              e.occurredAt,
              _
            )
          )
          .map(_ => ())
      case e: TaskEvent.TaskAssigned =>
        entityRef
          .askWithStatus[TaskActor.AssignResponse](
            TaskActor.Assign(e.userId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskCompleted =>
        entityRef
          .askWithStatus[TaskActor.CompleteResponse](
            TaskActor.Complete(e.actualQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: TaskEvent.TaskCancelled =>
        entityRef
          .askWithStatus[TaskActor.CancelResponse](
            TaskActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(entries: List[(Task, TaskEvent)]): Future[Unit] =
    Future.traverse(entries)((task, event) => save(task, event)).map(_ => ())
