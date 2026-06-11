package neon.handlingunit

import neon.common.HandlingUnitId
import neon.common.entity.PekkoEntityRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

/** Actor-backed implementation of [[AsyncHandlingUnitRepository]]. */
class PekkoHandlingUnitRepository(system: ActorSystem[?])(using Timeout)
    extends PekkoEntityRepository[HandlingUnitActor.Command, HandlingUnit](
      actorSystem = system,
      entityKey = HandlingUnitActor.EntityKey,
      behaviorFactory = HandlingUnitActor.apply,
      getState = HandlingUnitActor.GetState.apply
    )
    with AsyncHandlingUnitRepository:

  def findById(id: HandlingUnitId): Future[Option[HandlingUnit]] =
    findByEntityId(id.value.toString)

  def findByIds(ids: List[HandlingUnitId]): Future[List[HandlingUnit]] =
    Future
      .traverse(ids)(findById)
      .map(_.flatten)

  def save(
      handlingUnit: HandlingUnit,
      event: HandlingUnitEvent
  ): Future[Unit] =
    val ref = entityRef(handlingUnit.id.value.toString)
    val ensureInitialized =
      ref.askWithStatus(HandlingUnitActor.Create(handlingUnit, _))
    ensureInitialized.flatMap { _ =>
      event match
        case e: HandlingUnitEvent.HandlingUnitMovedToBuffer =>
          ref
            .askWithStatus[HandlingUnitActor.MoveToBufferResponse](
              HandlingUnitActor.MoveToBuffer(e.locationId, e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitEmptied =>
          ref
            .askWithStatus[HandlingUnitActor.EmptyResponse](
              HandlingUnitActor.Empty(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitPacked =>
          ref
            .askWithStatus[HandlingUnitActor.PackResponse](
              HandlingUnitActor.Pack(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitReadyToShip =>
          ref
            .askWithStatus[HandlingUnitActor.ReadyToShipResponse](
              HandlingUnitActor.ReadyToShip(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitShipped =>
          ref
            .askWithStatus[HandlingUnitActor.ShipResponse](
              HandlingUnitActor.Ship(e.occurredAt, _)
            )
            .map(_ => ())
    }
