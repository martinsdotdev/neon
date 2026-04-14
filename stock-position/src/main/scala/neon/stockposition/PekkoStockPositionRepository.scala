package neon.stockposition

import io.r2dbc.spi.ConnectionFactory
import neon.common.{R2dbcProjectionQueries, SkuId, StockPositionId, WarehouseAreaId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class PekkoStockPositionRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncStockPositionRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(StockPositionActor.EntityKey)(ctx => StockPositionActor(ctx.entityId))
  )

  def findById(id: StockPositionId): Future[Option[StockPosition]] =
    sharding
      .entityRefFor(StockPositionActor.EntityKey, id.value.toString)
      .ask(StockPositionActor.GetState(_))

  def findBySkuAndArea(
      skuId: SkuId,
      warehouseAreaId: WarehouseAreaId
  ): Future[List[StockPosition]] =
    queryProjectionIds(
      "SELECT stock_position_id FROM stock_position_by_sku_area WHERE sku_id = $1 AND warehouse_area_id = $2",
      List(skuId.value, warehouseAreaId.value),
      "stock_position_id"
    ).flatMap { ids =>
      Future.sequence(ids.map(id => findById(StockPositionId(id))))
    }.map(_.flatten)

  def save(stockPosition: StockPosition, event: StockPositionEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      StockPositionActor.EntityKey,
      stockPosition.id.value.toString
    )
    event match
      case e: StockPositionEvent.Created =>
        entityRef
          .askWithStatus(StockPositionActor.Create(stockPosition, e, _))
          .map(_ => ())
      case e: StockPositionEvent.Allocated =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Allocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Deallocated =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Deallocate(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.QuantityAdded =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.AddQuantity(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.AllocatedConsumed =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ConsumeAllocated(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Reserved =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Reserve(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.ReservationReleased =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ReleaseReservation(e.quantity, e.lockType, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Blocked =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Block(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Unblocked =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Unblock(e.quantity, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.Adjusted =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.Adjust(e.delta, e.reasonCode, e.occurredAt, _)
          )
          .map(_ => ())
      case e: StockPositionEvent.StatusChanged =>
        entityRef
          .askWithStatus[StockPositionActor.MutationResponse](
            StockPositionActor.ChangeStatus(e.newStatus, e.occurredAt, _)
          )
          .map(_ => ())
