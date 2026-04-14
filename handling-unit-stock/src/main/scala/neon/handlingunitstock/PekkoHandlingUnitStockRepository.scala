package neon.handlingunitstock

import io.r2dbc.spi.ConnectionFactory
import neon.common.{ContainerId, HandlingUnitStockId, R2dbcProjectionQueries}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class PekkoHandlingUnitStockRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncHandlingUnitStockRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(HandlingUnitStockActor.EntityKey)(ctx => HandlingUnitStockActor(ctx.entityId))
  )

  def findById(id: HandlingUnitStockId): Future[Option[HandlingUnitStock]] =
    sharding
      .entityRefFor(HandlingUnitStockActor.EntityKey, id.value.toString)
      .ask(HandlingUnitStockActor.GetState(_))

  def findByContainer(
      containerId: ContainerId
  ): Future[List[HandlingUnitStock]] =
    queryProjectionIds(
      "SELECT handling_unit_stock_id FROM handling_unit_stock_by_container WHERE container_id = $1",
      containerId.value,
      "handling_unit_stock_id"
    ).flatMap { ids =>
      Future.sequence(ids.map(id => findById(HandlingUnitStockId(id))))
    }.map(_.flatten)

  def save(
      handlingUnitStock: HandlingUnitStock,
      event: HandlingUnitStockEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      HandlingUnitStockActor.EntityKey,
      handlingUnitStock.id.value.toString
    )
    event match
      case e: HandlingUnitStockEvent.Created =>
        entityRef
          .askWithStatus(HandlingUnitStockActor.Create(handlingUnitStock, e, _))
          .map(_ => ())
      case e: HandlingUnitStockEvent.Allocated =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Allocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Deallocated =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Deallocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.QuantityAdded =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.AddQuantity(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.AllocatedConsumed =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ConsumeAllocated(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Reserved =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Reserve(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.ReservationReleased =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ReleaseReservation(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Blocked =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Block(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Unblocked =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Unblock(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Adjusted =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Adjust(e.delta, e.reasonCode, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.StatusChanged =>
        entityRef
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ChangeStatus(e.newStatus, e.occurredAt, _)
          )
          .map(_ => ())
