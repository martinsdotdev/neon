package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.inventory.InventoryEvent

import java.time.Instant

class InventoryProjectionHandlerSuite extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = InventoryProjectionHandler()

  describe("InventoryProjectionHandler"):

    it(
      "inserts into inventory_by_location_sku_lot " +
        "on InventoryCreated"
    ):
      val inventoryId = InventoryId()
      val locationId = LocationId()
      val skuId = SkuId()

      val event = InventoryEvent.InventoryCreated(
        inventoryId = inventoryId,
        locationId = locationId,
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
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}'"
      )
      assert(count == 1L)

    it(
      "inserts with null lot " +
        "on InventoryCreated without lot"
    ):
      val inventoryId = InventoryId()
      val locationId = LocationId()
      val skuId = SkuId()

      val event = InventoryEvent.InventoryCreated(
        inventoryId = inventoryId,
        locationId = locationId,
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
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}'"
      )
      assert(count == 1L)

    it("increments reserved on InventoryReserved"):
      val inventoryId = InventoryId()
      val locationId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = inventoryId,
        locationId = locationId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = None,
        onHand = 100,
        occurredAt = Instant.now()
      )

      val reserved = InventoryEvent.InventoryReserved(
        inventoryId = inventoryId,
        locationId = locationId,
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
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}' " +
          "AND reserved = 10"
      )
      assert(count == 1L)

    it(
      "decrements on_hand and reserved " +
        "on InventoryConsumed"
    ):
      val inventoryId = InventoryId()
      val locationId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = inventoryId,
        locationId = locationId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = None,
        onHand = 100,
        occurredAt = Instant.now()
      )

      val reserved = InventoryEvent.InventoryReserved(
        inventoryId = inventoryId,
        locationId = locationId,
        skuId = skuId,
        lot = None,
        quantityReserved = 20,
        occurredAt = Instant.now()
      )

      val consumed = InventoryEvent.InventoryConsumed(
        inventoryId = inventoryId,
        locationId = locationId,
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
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              reserved,
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              consumed,
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
      }

      val countOnHand = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}' " +
          "AND on_hand = 85"
      )
      assert(countOnHand == 1L)

      val countReserved = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}' " +
          "AND reserved = 5"
      )
      assert(countReserved == 1L)

    it("updates lot on LotCorrected"):
      val inventoryId = InventoryId()
      val locationId = LocationId()
      val skuId = SkuId()

      val created = InventoryEvent.InventoryCreated(
        inventoryId = inventoryId,
        locationId = locationId,
        skuId = skuId,
        packagingLevel = PackagingLevel.Each,
        lot = Some(Lot("OLD-LOT")),
        onHand = 50,
        occurredAt = Instant.now()
      )

      val corrected = InventoryEvent.LotCorrected(
        inventoryId = inventoryId,
        locationId = locationId,
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
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              corrected,
              s"Inventory|${inventoryId.value}",
              "Inventory"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM inventory_by_location_sku_lot " +
          "WHERE inventory_id = " +
          s"'${inventoryId.value}' " +
          "AND lot = 'NEW-LOT'"
      )
      assert(count == 1L)
