package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class InventorySuite extends AnyFunSpec:
  val locationId = LocationId()
  val skuId = SkuId()
  val packagingLevel = PackagingLevel.Each
  val lot = Some(Lot("LOT-001"))
  val at = Instant.now()

  def anInventory(onHand: Int = 100, reserved: Int = 0, inventoryLot: Option[Lot] = lot) =
    val (inventory, _) =
      Inventory.create(
        locationId = locationId,
        skuId = skuId,
        packagingLevel = packagingLevel,
        lot = inventoryLot,
        onHand = onHand,
        at = at
      )
    if reserved > 0 then inventory.reserve(quantity = reserved, at = at)._1 else inventory

  describe("Inventory"):
    describe("creating"):
      it("produces an Inventory with reserved = 0 and an InventoryCreated event"):
        val (inventory, event) = Inventory.create(
          locationId = locationId,
          skuId = skuId,
          packagingLevel = packagingLevel,
          lot = lot,
          onHand = 50,
          at = at
        )
        assert(inventory.id == event.inventoryId)
        assert(inventory.locationId == locationId)
        assert(inventory.skuId == skuId)
        assert(inventory.packagingLevel == packagingLevel)
        assert(inventory.lot == lot)
        assert(inventory.onHand == 50)
        assert(inventory.reserved == 0)

      it("rejects negative onHand"):
        assertThrows[IllegalArgumentException]:
          Inventory.create(
            locationId = locationId,
            skuId = skuId,
            packagingLevel = packagingLevel,
            lot = lot,
            onHand = -1,
            at = at
          )

      it("allows onHand = 0 for empty position tracking"):
        val (inventory, _) = Inventory.create(
          locationId = locationId,
          skuId = skuId,
          packagingLevel = packagingLevel,
          lot = lot,
          onHand = 0,
          at = at
        )
        assert(inventory.onHand == 0)

      it("event carries all fields for downstream consumers"):
        val (_, event) = Inventory.create(
          locationId = locationId,
          skuId = skuId,
          packagingLevel = packagingLevel,
          lot = lot,
          onHand = 50,
          at = at
        )
        assert(event.locationId == locationId)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == packagingLevel)
        assert(event.lot == lot)
        assert(event.onHand == 50)
        assert(event.occurredAt == at)

    describe("available"):
      it("is onHand minus reserved"):
        val inventory = anInventory(onHand = 100, reserved = 30)
        assert(inventory.available == 70)

      it("is zero when fully reserved"):
        val inventory = anInventory(onHand = 50, reserved = 50)
        assert(inventory.available == 0)

    describe("reserving"):
      it("increments reserved and emits InventoryReserved"):
        val (updated, event) =
          anInventory(onHand = 100, reserved = 10).reserve(quantity = 20, at = at)
        assert(updated.reserved == 30)
        assert(updated.onHand == 100)
        assert(event.quantityReserved == 20)

      it("rejects quantity exceeding available"):
        val inventory = anInventory(onHand = 100, reserved = 90)
        assertThrows[IllegalArgumentException]:
          inventory.reserve(quantity = 11, at = at)

      it("allows reserving exactly the available amount"):
        val inventory = anInventory(onHand = 100, reserved = 90)
        val (updated, _) = inventory.reserve(quantity = 10, at = at)
        assert(updated.available == 0)

      it("rejects quantity <= 0"):
        assertThrows[IllegalArgumentException]:
          anInventory().reserve(quantity = 0, at = at)
        assertThrows[IllegalArgumentException]:
          anInventory().reserve(quantity = -1, at = at)

      it("event carries inventory identity and lot"):
        val inventory = anInventory()
        val (_, event) = inventory.reserve(quantity = 5, at = at)
        assert(event.inventoryId == inventory.id)
        assert(event.locationId == locationId)
        assert(event.skuId == skuId)
        assert(event.lot == lot)
        assert(event.occurredAt == at)

    describe("releasing"):
      it("decrements reserved and emits InventoryReleased"):
        val (updated, event) =
          anInventory(onHand = 100, reserved = 30).release(quantity = 10, at = at)
        assert(updated.reserved == 20)
        assert(updated.onHand == 100)
        assert(event.quantityReleased == 10)

      it("rejects quantity exceeding reserved"):
        val inventory = anInventory(onHand = 100, reserved = 10)
        assertThrows[IllegalArgumentException]:
          inventory.release(quantity = 11, at = at)

      it("allows releasing exactly the reserved amount"):
        val (updated, _) = anInventory(onHand = 100, reserved = 30).release(quantity = 30, at = at)
        assert(updated.reserved == 0)

      it("rejects quantity <= 0"):
        assertThrows[IllegalArgumentException]:
          anInventory(onHand = 100, reserved = 30).release(quantity = 0, at = at)
        assertThrows[IllegalArgumentException]:
          anInventory(onHand = 100, reserved = 30).release(quantity = -1, at = at)

      it("event carries inventory identity and lot"):
        val inventory = anInventory(onHand = 100, reserved = 30)
        val (_, event) = inventory.release(quantity = 10, at = at)
        assert(event.inventoryId == inventory.id)
        assert(event.locationId == locationId)
        assert(event.skuId == skuId)
        assert(event.lot == lot)
        assert(event.occurredAt == at)

    describe("consuming"):
      it("decrements both onHand and reserved"):
        val (updated, event) =
          anInventory(onHand = 100, reserved = 30).consume(quantity = 20, at = at)
        assert(updated.onHand == 80)
        assert(updated.reserved == 10)
        assert(event.quantityConsumed == 20)

      it("rejects quantity exceeding reserved"):
        val inventory = anInventory(onHand = 100, reserved = 10)
        assertThrows[IllegalArgumentException]:
          inventory.consume(quantity = 11, at = at)

      it("allows consuming exactly the reserved amount"):
        val (updated, _) = anInventory(onHand = 100, reserved = 30).consume(quantity = 30, at = at)
        assert(updated.onHand == 70)
        assert(updated.reserved == 0)

      it("rejects quantity <= 0"):
        assertThrows[IllegalArgumentException]:
          anInventory(onHand = 100, reserved = 30).consume(quantity = 0, at = at)
        assertThrows[IllegalArgumentException]:
          anInventory(onHand = 100, reserved = 30).consume(quantity = -1, at = at)

      it("event carries inventory identity and lot"):
        val inventory = anInventory(onHand = 100, reserved = 30)
        val (_, event) = inventory.consume(quantity = 10, at = at)
        assert(event.inventoryId == inventory.id)
        assert(event.locationId == locationId)
        assert(event.skuId == skuId)
        assert(event.lot == lot)
        assert(event.occurredAt == at)

    describe("correcting lot"):
      it("changes the lot and emits LotCorrected with previous and new"):
        val inventory = anInventory(inventoryLot = Some(Lot("OLD")))
        val (updated, event) = inventory.correctLot(newLot = Some(Lot("NEW")), at = at)
        assert(updated.lot == Some(Lot("NEW")))
        assert(event.previousLot == Some(Lot("OLD")))
        assert(event.newLot == Some(Lot("NEW")))

      it("can set lot from None to Some"):
        val inventory = anInventory(inventoryLot = None)
        val (updated, event) = inventory.correctLot(newLot = Some(Lot("LOT-X")), at = at)
        assert(updated.lot == Some(Lot("LOT-X")))
        assert(event.previousLot == None)
        assert(event.newLot == Some(Lot("LOT-X")))

      it("can clear lot from Some to None"):
        val inventory = anInventory(inventoryLot = Some(Lot("LOT-X")))
        val (updated, event) = inventory.correctLot(newLot = None, at = at)
        assert(updated.lot == None)
        assert(event.previousLot == Some(Lot("LOT-X")))
        assert(event.newLot == None)

      it("emits event even when correcting to the same lot"):
        val inventory = anInventory(inventoryLot = Some(Lot("SAME")))
        val (updated, event) = inventory.correctLot(newLot = Some(Lot("SAME")), at = at)
        assert(updated.lot == Some(Lot("SAME")))
        assert(event.previousLot == Some(Lot("SAME")))
        assert(event.newLot == Some(Lot("SAME")))

      it("event carries inventory identity"):
        val inventory = anInventory()
        val (_, event) = inventory.correctLot(newLot = Some(Lot("NEW")), at = at)
        assert(event.inventoryId == inventory.id)
        assert(event.locationId == locationId)
        assert(event.skuId == skuId)
        assert(event.occurredAt == at)

      it("rejects correction when units are reserved"):
        val inventory = anInventory(onHand = 100, reserved = 10)
        assertThrows[IllegalArgumentException]:
          inventory.correctLot(newLot = Some(Lot("NEW")), at = at)

      it("succeeds when reserved is zero"):
        val inventory = anInventory(onHand = 100, reserved = 0)
        val (updated, _) = inventory.correctLot(newLot = Some(Lot("NEW")), at = at)
        assert(updated.lot == Some(Lot("NEW")))

    describe("lotless inventory"):
      it("reserve and consume work without lot tracking"):
        val inventory = anInventory(onHand = 50, inventoryLot = None)
        val (reserved, reserveEvent) = inventory.reserve(quantity = 10, at = at)
        assert(reserveEvent.lot == None)
        val (consumed, consumeEvent) = reserved.consume(quantity = 10, at = at)
        assert(consumeEvent.lot == None)
        assert(consumed.onHand == 40)
        assert(consumed.reserved == 0)

    describe("lifecycle"):
      it("stock arrives, gets allocated, and leaves the position"):
        val (inventory, created) = Inventory.create(
          locationId = locationId,
          skuId = skuId,
          packagingLevel = packagingLevel,
          lot = lot,
          onHand = 30,
          at = at
        )
        assert(created.onHand == 30)

        val (reserved, _) = inventory.reserve(quantity = 20, at = at)
        assert(reserved.available == 10)

        val (consumed, _) = reserved.consume(quantity = 20, at = at)
        assert(consumed.onHand == 10)
        assert(consumed.reserved == 0)
        assert(consumed.available == 10)
