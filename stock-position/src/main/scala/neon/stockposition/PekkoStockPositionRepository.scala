package neon.stockposition

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{R2dbcProjectionQueries, SkuId, StockPositionId, WarehouseAreaId}
import neon.stockposition.StockPositionProjectionSchema.StockPositionBySkuArea
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoStockPositionRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[StockPositionActor.Command, StockPosition](
      actorSystem = actorSystem,
      entityKey = StockPositionActor.EntityKey,
      behaviorFactory = StockPositionActor.apply,
      getState = StockPositionActor.GetState.apply
    )
    with AsyncStockPositionRepository
    with R2dbcProjectionQueries:

  def findById(id: StockPositionId): Future[Option[StockPosition]] =
    findByEntityId(id.value.toString)

  def findBySkuAndArea(
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId
  ): Future[List[StockPosition]] =
    queryProjectionIds(
      sql = StockPositionBySkuArea.SelectStockPositionIdsBySkuAndArea,
      params = List(skuId.value, warehouseAreaId.value),
      idColumn = StockPositionBySkuArea.StockPositionId
    ).flatMap { ids =>
      Future.sequence(ids.map(id => findById(StockPositionId(id))))
    }.map(_.flatten)

  def save(stockPosition: StockPosition, event: StockPositionEvent): Future[Unit] =
    val ref = entityRef(stockPosition.id.value.toString)
    event match
      case e: StockPositionEvent.Created =>
        ref
          .askWithStatus(StockPositionActor.Create(stockPosition, e, _))
          .map(_ => ())
      case e: StockPositionEvent.Allocated =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Allocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Deallocated =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Deallocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.QuantityAdded =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.AddQuantity(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.AllocatedConsumed =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ConsumeAllocated(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Reserved =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Reserve(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.ReservationReleased =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ReleaseReservation(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Blocked =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Block(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Unblocked =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Unblock(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Adjusted =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Adjust(e.delta, e.reasonCode, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.StatusChanged =>
        ref
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ChangeStatus(e.newStatus, e.occurredAt, _)
          )
          .map(_ => ())
