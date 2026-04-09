package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.inventory.InventoryEvent
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class InventoryProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = InventoryProjectionHandler()

  private def withSession(
      f: R2dbcSession => Unit
  ): Unit =
    val connection =
      Mono.from(connectionFactory.create()).block()
    try
      val session = new R2dbcSession(connection)(using
        system.executionContext,
        system
      )
      f(session)
    finally Mono.from(connection.close()).block()

  private def envelope[E](
      event: E,
      persistenceId: String
  ): EventEnvelope[E] =
    new EventEnvelope[E](
      offset = TimestampOffset.Zero,
      persistenceId = persistenceId,
      sequenceNr = 1L,
      eventOption = Some(event),
      timestamp = System.currentTimeMillis(),
      eventMetadata = None,
      entityType = "Inventory",
      slice = 0
    )

  describe("InventoryProjectionHandler") {

    it(
      "should insert into inventory_by_location_sku_lot on InventoryCreated"
    ) {
      val invId = InventoryId()
      val locId = LocationId()
      val skuId = SkuId()

      val event = InventoryEvent.InventoryCreated(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = Some(Lot("LOT-001")),
        onHand = 100,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should insert with null lot on InventoryCreated without lot"
    ) {
      val invId = InventoryId()
      val locId = LocationId()
      val skuId = SkuId()

      val event = InventoryEvent.InventoryCreated(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Case,
        lot = None,
        onHand = 50,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should increment reserved on InventoryReserved"
    ) {
      val invId = InventoryId()
      val locId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = None,
        onHand = 100,
        occurredAt = Instant.now()
      )

      val reserved = InventoryEvent.InventoryReserved(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        lot = None,
        quantityReserved = 10,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}' AND reserved = 10"
      )
      assert(count == 1L)
    }

    it(
      "should decrement on_hand and reserved on InventoryConsumed"
    ) {
      val invId = InventoryId()
      val locId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = None,
        onHand = 100,
        occurredAt = Instant.now()
      )

      val reserved = InventoryEvent.InventoryReserved(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        lot = None,
        quantityReserved = 20,
        occurredAt = Instant.now()
      )

      val consumed = InventoryEvent.InventoryConsumed(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        lot = None,
        quantityConsumed = 15,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              consumed,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
      }

      val countOnHand = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}' AND on_hand = 85"
      )
      assert(countOnHand == 1L)

      val countReserved = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}' AND reserved = 5"
      )
      assert(countReserved == 1L)
    }

    it("should update lot on LotCorrected") {
      val invId = InventoryId()
      val locId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = Some(Lot("OLD-LOT")),
        onHand = 50,
        occurredAt = Instant.now()
      )

      val corrected = InventoryEvent.LotCorrected(
        inventoryId = invId,
        locationId = locId,
        skuId = skuId,
        previousLot = Some(Lot("OLD-LOT")),
        newLot = Some(Lot("NEW-LOT")),
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              corrected,
              s"Inventory|${invId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM inventory_by_location_sku_lot WHERE inventory_id = '${invId.value}' AND lot = 'NEW-LOT'"
      )
      assert(count == 1L)
    }
  }
