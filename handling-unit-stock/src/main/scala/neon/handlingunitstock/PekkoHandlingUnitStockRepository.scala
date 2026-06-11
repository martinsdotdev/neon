package neon.handlingunitstock

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{ContainerId, HandlingUnitStockId, R2dbcProjectionQueries}
import neon.handlingunitstock.HandlingUnitStockProjectionSchema.HandlingUnitStockByContainer
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoHandlingUnitStockRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[HandlingUnitStockActor.Command, HandlingUnitStock](
      actorSystem = actorSystem,
      entityKey = HandlingUnitStockActor.EntityKey,
      behaviorFactory = HandlingUnitStockActor.apply,
      getState = HandlingUnitStockActor.GetState.apply
    )
    with AsyncHandlingUnitStockRepository
    with R2dbcProjectionQueries:

  def findById(id: HandlingUnitStockId): Future[Option[HandlingUnitStock]] =
    findByEntityId(id.value.toString)

  def findByContainer(
      containerId: ContainerId
  ): Future[List[HandlingUnitStock]] =
    queryProjectionIds(
      sql = HandlingUnitStockByContainer.SelectHandlingUnitStockIdsByContainerId,
      param = containerId.value,
      idColumn = HandlingUnitStockByContainer.HandlingUnitStockId
    ).flatMap { ids =>
      Future.sequence(ids.map(id => findById(HandlingUnitStockId(id))))
    }.map(_.flatten)

  def save(
      handlingUnitStock: HandlingUnitStock,
      event: HandlingUnitStockEvent
  ): Future[Unit] =
    val ref = entityRef(handlingUnitStock.id.value.toString)
    event match
      case e: HandlingUnitStockEvent.Created =>
        ref
          .askWithStatus(HandlingUnitStockActor.Create(handlingUnitStock, e, _))
          .map(_ => ())
      case e: HandlingUnitStockEvent.Allocated =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Allocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Deallocated =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Deallocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.QuantityAdded =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.AddQuantity(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.AllocatedConsumed =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ConsumeAllocated(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Reserved =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Reserve(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.ReservationReleased =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ReleaseReservation(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Blocked =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Block(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Unblocked =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Unblock(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.Adjusted =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.Adjust(e.delta, e.reasonCode, e.occurredAt, _)
          )
          .map(_ => ())
      case e: HandlingUnitStockEvent.StatusChanged =>
        ref
          .askWithStatus[HandlingUnitStockActor.MutationResponse](
            HandlingUnitStockActor.ChangeStatus(e.newStatus, e.occurredAt, _)
          )
          .map(_ => ())
