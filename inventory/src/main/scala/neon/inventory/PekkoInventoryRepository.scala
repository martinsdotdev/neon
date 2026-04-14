package neon.inventory

import io.r2dbc.spi.ConnectionFactory
import neon.common.{InventoryId, LocationId, Lot, R2dbcProjectionQueries, SkuId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.Timeout

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PekkoInventoryRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncInventoryRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(InventoryActor.EntityKey)(ctx => InventoryActor(ctx.entityId))
  )

  def findById(id: InventoryId): Future[Option[Inventory]] =
    sharding
      .entityRefFor(InventoryActor.EntityKey, id.value.toString)
      .ask(InventoryActor.GetState(_))

  def findByLocationSkuLot(
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot]
  ): Future[Option[Inventory]] =
    val lotValue = lot.map(_.value).orNull
    Source
      .fromPublisher(connectionFactory.create())
      .runWith(Sink.head)
      .flatMap { connection =>
        val stmt = connection
          .createStatement(
            "SELECT inventory_id FROM inventory_by_location_sku_lot WHERE location_id = $1 AND sku_id = $2 AND lot IS NOT DISTINCT FROM $3"
          )
          .bind(0, locationId.value)
          .bind(1, skuId.value)
        if lotValue != null then stmt.bind(2, lotValue)
        else stmt.bindNull(2, classOf[String])
        Source
          .fromPublisher(stmt.execute())
          .flatMapConcat(result =>
            Source.fromPublisher(
              result.map((row, _) => row.get("inventory_id", classOf[UUID]))
            )
          )
          .runWith(Sink.headOption)
          .flatMap {
            case Some(id) => findById(InventoryId(id))
            case None     => Future.successful(None)
          }
          .andThen { case _ =>
            Source.fromPublisher(connection.close()).runWith(Sink.ignore)
          }
      }

  def save(inventory: Inventory, event: InventoryEvent): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      InventoryActor.EntityKey,
      inventory.id.value.toString
    )
    event match
      case e: InventoryEvent.InventoryCreated =>
        entityRef
          .askWithStatus(
            InventoryActor.Create(inventory, e, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryReserved =>
        entityRef
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Reserve(e.quantityReserved, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryReleased =>
        entityRef
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Release(e.quantityReleased, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryConsumed =>
        entityRef
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Consume(e.quantityConsumed, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.LotCorrected =>
        entityRef
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.CorrectLot(e.newLot, e.occurredAt, _)
          )
          .map(_ => ())
