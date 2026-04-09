package neon.cyclecount

import neon.common.CycleCountId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncCycleCountRepository]]. Single-entity operations route to
  * the CycleCountActor via Cluster Sharding ask pattern.
  */
class PekkoCycleCountRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncCycleCountRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(CycleCountActor.EntityKey)(ctx => CycleCountActor(ctx.entityId)))

  def findById(id: CycleCountId): Future[Option[CycleCount]] =
    sharding
      .entityRefFor(CycleCountActor.EntityKey, id.value.toString)
      .ask(CycleCountActor.GetState(_))

  def save(cycleCount: CycleCount, event: CycleCountEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      CycleCountActor.EntityKey,
      cycleCount.id.value.toString
    )
    event match
      case e: CycleCountEvent.CycleCountCreated =>
        entityRef
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
        entityRef
          .askWithStatus[CycleCountActor.StartResponse](
            CycleCountActor.Start(e.occurredAt, _)
          )
          .map(_ => ())
      case e: CycleCountEvent.CycleCountCompleted =>
        entityRef
          .askWithStatus[CycleCountActor.CompleteResponse](
            CycleCountActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: CycleCountEvent.CycleCountCancelled =>
        entityRef
          .askWithStatus[CycleCountActor.CancelResponse](
            CycleCountActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
