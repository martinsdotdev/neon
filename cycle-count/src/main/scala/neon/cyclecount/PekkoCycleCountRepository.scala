package neon.cyclecount

import neon.common.CycleCountId
import neon.common.entity.PekkoEntityRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncCycleCountRepository]]. Single-entity operations route to
  * the CycleCountActor via Cluster Sharding ask pattern.
  */
class PekkoCycleCountRepository(system: ActorSystem[?])(using Timeout)
    extends PekkoEntityRepository[CycleCountActor.Command, CycleCount](
      actorSystem = system,
      entityKey = CycleCountActor.EntityKey,
      behaviorFactory = CycleCountActor.apply,
      getState = CycleCountActor.GetState.apply
    )
    with AsyncCycleCountRepository:

  def findById(id: CycleCountId): Future[Option[CycleCount]] =
    findByEntityId(id.value.toString)

  def save(cycleCount: CycleCount, event: CycleCountEvent): Future[Unit] =
    val ref = entityRef(cycleCount.id.value.toString)
    event match
      case e: CycleCountEvent.CycleCountCreated =>
        ref
          .askWithStatus(
            CycleCountActor.Create(
              CycleCount.New(
                e.cycleCountId,
                e.warehouseAreaId,
                e.skuIds,
                e.countType,
                e.countMethod
              ),
              e,
              _
            )
          )
          .map(_ => ())
      case e: CycleCountEvent.CycleCountStarted =>
        ref
          .askWithStatus[CycleCountActor.StartResponse](
            CycleCountActor.Start(e.occurredAt, _)
          )
          .map(_ => ())
      case e: CycleCountEvent.CycleCountCompleted =>
        ref
          .askWithStatus[CycleCountActor.CompleteResponse](
            CycleCountActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: CycleCountEvent.CycleCountCancelled =>
        ref
          .askWithStatus[CycleCountActor.CancelResponse](
            CycleCountActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
