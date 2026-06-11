package neon.inventory

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{InventoryId, LocationId, Lot, R2dbcProjectionQueries, SkuId}
import neon.inventory.InventoryProjectionSchema.InventoryByLocationSkuLot
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.Timeout

import java.util.UUID
import scala.concurrent.Future

class PekkoInventoryRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[InventoryActor.Command, Inventory](
      actorSystem = actorSystem,
      entityKey = InventoryActor.EntityKey,
      behaviorFactory = InventoryActor.apply,
      getState = InventoryActor.GetState.apply
    )
    with AsyncInventoryRepository
    with R2dbcProjectionQueries:

  def findById(id: InventoryId): Future[Option[Inventory]] =
    findByEntityId(id.value.toString)

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
            InventoryByLocationSkuLot.SelectInventoryIdByLocationSkuLot
          )
          .bind(0, locationId.value)
          .bind(1, skuId.value)
        if lotValue != null then stmt.bind(2, lotValue)
        else stmt.bindNull(2, classOf[String])
        Source
          .fromPublisher(stmt.execute())
          .flatMapConcat(result =>
            Source.fromPublisher(
              result.map((row, _) => row.get(InventoryByLocationSkuLot.InventoryId, classOf[UUID]))
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
    val ref = entityRef(inventory.id.value.toString)
    event match
      case e: InventoryEvent.InventoryCreated =>
        ref
          .askWithStatus(
            InventoryActor.Create(inventory, e, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryReserved =>
        ref
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Reserve(e.quantityReserved, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryReleased =>
        ref
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Release(e.quantityReleased, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.InventoryConsumed =>
        ref
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.Consume(e.quantityConsumed, e.occurredAt, _)
          )
          .map(_ => ())
      case e: InventoryEvent.LotCorrected =>
        ref
          .askWithStatus[InventoryActor.MutationResponse](
            InventoryActor.CorrectLot(e.newLot, e.occurredAt, _)
          )
          .map(_ => ())
