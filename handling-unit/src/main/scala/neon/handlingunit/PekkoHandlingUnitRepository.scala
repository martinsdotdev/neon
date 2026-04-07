package neon.handlingunit

import neon.common.HandlingUnitId
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncHandlingUnitRepository]]. */
class PekkoHandlingUnitRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncHandlingUnitRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(HandlingUnitActor.EntityKey)(ctx => HandlingUnitActor(ctx.entityId))
  )

  def findById(id: HandlingUnitId): Future[Option[HandlingUnit]] =
    sharding
      .entityRefFor(HandlingUnitActor.EntityKey, id.value.toString)
      .ask(HandlingUnitActor.GetState(_))

  def findByIds(ids: List[HandlingUnitId]): Future[List[HandlingUnit]] =
    Future
      .traverse(ids)(findById)
      .map(_.flatten)

  def save(
      handlingUnit: HandlingUnit,
      event: HandlingUnitEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      HandlingUnitActor.EntityKey,
      handlingUnit.id.value.toString
    )
    val ensureInitialized =
      entityRef.askWithStatus(HandlingUnitActor.Create(handlingUnit, _))
    ensureInitialized.flatMap { _ =>
      event match
        case e: HandlingUnitEvent.HandlingUnitMovedToBuffer =>
          entityRef
            .askWithStatus[HandlingUnitActor.MoveToBufferResponse](
              HandlingUnitActor.MoveToBuffer(e.locationId, e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitEmptied =>
          entityRef
            .askWithStatus[HandlingUnitActor.EmptyResponse](
              HandlingUnitActor.Empty(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitPacked =>
          entityRef
            .askWithStatus[HandlingUnitActor.PackResponse](
              HandlingUnitActor.Pack(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitReadyToShip =>
          entityRef
            .askWithStatus[HandlingUnitActor.ReadyToShipResponse](
              HandlingUnitActor.ReadyToShip(e.occurredAt, _)
            )
            .map(_ => ())
        case e: HandlingUnitEvent.HandlingUnitShipped =>
          entityRef
            .askWithStatus[HandlingUnitActor.ShipResponse](
              HandlingUnitActor.Ship(e.occurredAt, _)
            )
            .map(_ => ())
    }
