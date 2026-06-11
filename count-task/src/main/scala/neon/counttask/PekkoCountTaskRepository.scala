package neon.counttask

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{CountTaskId, CycleCountId, R2dbcProjectionQueries}
import neon.counttask.CountTaskProjectionSchema.CountTaskByCycleCount
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncCountTaskRepository]]. Single-entity operations route to
  * the CountTaskActor via Cluster Sharding ask pattern. Cross-entity queries use R2DBC projection
  * tables.
  */
class PekkoCountTaskRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[CountTaskActor.Command, CountTask](
      actorSystem = actorSystem,
      entityKey = CountTaskActor.EntityKey,
      behaviorFactory = CountTaskActor.apply,
      getState = CountTaskActor.GetState.apply
    )
    with AsyncCountTaskRepository
    with R2dbcProjectionQueries:

  def findById(id: CountTaskId): Future[Option[CountTask]] =
    findByEntityId(id.value.toString)

  def findByCycleCountId(cycleCountId: CycleCountId): Future[List[CountTask]] =
    queryProjectionIds(
      sql = CountTaskByCycleCount.SelectCountTaskIdsByCycleCountId,
      param = cycleCountId.value,
      idColumn = CountTaskByCycleCount.CountTaskId
    ).flatMap(ids => Future.sequence(ids.map(id => findById(CountTaskId(id)))).map(_.flatten))

  def save(countTask: CountTask, event: CountTaskEvent): Future[Unit] =
    val ref = entityRef(countTask.id.value.toString)
    event match
      case e: CountTaskEvent.CountTaskCreated =>
        ref
          .askWithStatus(
            CountTaskActor.Create(countTask.asInstanceOf[CountTask.Pending], e, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskAssigned =>
        ref
          .askWithStatus[CountTaskActor.AssignResponse](
            CountTaskActor.Assign(e.userId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskRecorded =>
        ref
          .askWithStatus[CountTaskActor.RecordResponse](
            CountTaskActor.Record(e.actualQuantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: CountTaskEvent.CountTaskCancelled =>
        ref
          .askWithStatus[CountTaskActor.CancelResponse](
            CountTaskActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(entries: List[(CountTask, CountTaskEvent)]): Future[Unit] =
    sequenceSaves(entries)(save)
