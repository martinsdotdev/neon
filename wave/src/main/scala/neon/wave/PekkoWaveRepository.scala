package neon.wave

import neon.common.WaveId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncWaveRepository]]. Single-entity operations route to the
  * WaveActor via Cluster Sharding ask pattern.
  */
class PekkoWaveRepository(system: ActorSystem[?])(using Timeout) extends AsyncWaveRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(Entity(WaveActor.EntityKey)(ctx => WaveActor(ctx.entityId)))

  def findById(id: WaveId): Future[Option[Wave]] =
    sharding
      .entityRefFor(WaveActor.EntityKey, id.value.toString)
      .ask(WaveActor.GetState(_))

  def save(wave: Wave, event: WaveEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      WaveActor.EntityKey,
      wave.id.value.toString
    )
    event match
      case e: WaveEvent.WaveReleased =>
        entityRef
          .askWithStatus(
            WaveActor.Create(Wave.Planned(wave.id, wave.orderGrouping, e.orderIds), e, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCompleted =>
        entityRef
          .askWithStatus[WaveActor.CompleteResponse](
            WaveActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCancelled =>
        entityRef
          .askWithStatus[WaveActor.CancelResponse](
            WaveActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
