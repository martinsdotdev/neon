package neon.wave

import neon.common.WaveId
import neon.common.entity.PekkoEntityRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncWaveRepository]]. Single-entity operations route to the
  * WaveActor via Cluster Sharding ask pattern.
  */
class PekkoWaveRepository(system: ActorSystem[?])(using Timeout)
    extends PekkoEntityRepository[WaveActor.Command, Wave](
      actorSystem = system,
      entityKey = WaveActor.EntityKey,
      behaviorFactory = WaveActor.apply,
      getState = WaveActor.GetState.apply
    )
    with AsyncWaveRepository:

  def findById(id: WaveId): Future[Option[Wave]] =
    findByEntityId(id.value.toString)

  def save(wave: Wave, event: WaveEvent): Future[Unit] =
    val ref = entityRef(wave.id.value.toString)
    event match
      case e: WaveEvent.WaveReleased =>
        ref
          .askWithStatus(
            WaveActor.Create(Wave.Planned(wave.id, wave.orderGrouping, e.orderIds), e, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCompleted =>
        ref
          .askWithStatus[WaveActor.CompleteResponse](
            WaveActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: WaveEvent.WaveCancelled =>
        ref
          .askWithStatus[WaveActor.CancelResponse](
            WaveActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())
