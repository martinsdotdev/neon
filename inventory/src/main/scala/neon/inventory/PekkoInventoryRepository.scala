package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, SkuId}

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncInventoryRepository]]. */
class PekkoInventoryRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncInventoryRepository:

  private given ExecutionContext = system.executionContext
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
    // TODO: query inventory_by_location_sku_lot projection table
    Future.successful(None)

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
