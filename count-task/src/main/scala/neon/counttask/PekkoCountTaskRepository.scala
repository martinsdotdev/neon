package neon.counttask

import io.r2dbc.spi.ConnectionFactory
import neon.common.{CountTaskId, CycleCountId, R2dbcProjectionQueries}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncCountTaskRepository]]. Single-entity operations route to
  * the CountTaskActor via Cluster Sharding ask pattern. Cross-entity queries use R2DBC projection
  * tables.
  */
class PekkoCountTaskRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncCountTaskRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(CountTaskActor.EntityKey)(ctx => CountTaskActor(ctx.entityId)))

  def findById(id: CountTaskId): Future[Option[CountTask]] =
    sharding
      .entityRefFor(CountTaskActor.EntityKey, id.value.toString)
      .ask(CountTaskActor.GetState(_))

  def findByCycleCountId(cycleCountId: CycleCountId): Future[List[CountTask]] =
    queryProjectionIds(
      "SELECT count_task_id FROM count_task_by_cycle_count WHERE cycle_count_id = $1",
      cycleCountId.value,
      "count_task_id"
    ).flatMap(ids => Future.sequence(ids.map(id => findById(CountTaskId(id)))).map(_.flatten))

  def save(countTask: CountTask, event: CountTaskEvent): Future[Unit] =
    val entityRef =
      sharding.entityRefFor(CountTaskActor.EntityKey, countTask.id.value.toString)
    event match
      case e: CountTaskEvent.CountTaskCreated =>
        entityRef
          .askWithStatus(
            CountTaskActor.Create(countTask.asInstanceOf[CountTask.Pending], e, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskAssigned =>
        entityRef
          .askWithStatus[CountTaskActor.AssignResponse](
            CountTaskActor.Assign(e.userId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskRecorded =>
        entityRef
          .askWithStatus[CountTaskActor.RecordResponse](
            CountTaskActor.Record(e.actualQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskCancelled =>
        entityRef
          .askWithStatus[CountTaskActor.CancelResponse](
            CountTaskActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(entries: List[(CountTask, CountTaskEvent)]): Future[Unit] =
    Future.sequence(entries.map((countTask, event) => save(countTask, event))).map(_ => ())
