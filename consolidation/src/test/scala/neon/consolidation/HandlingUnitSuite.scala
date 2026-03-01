package neon.consolidationgroup

import neon.common.{HandlingUnitId, LocationId, PackagingLevel}
import org.scalatest.funspec.AnyFunSpec
import java.time.Instant

class HandlingUnitSuite extends AnyFunSpec:
  val id = HandlingUnitId()
  val pickFace = LocationId()
  val bufferArea = LocationId()
  val packStation = LocationId()
  val at = Instant.now()

  def pickCreated() =
    HandlingUnit.Created(id, HandlingUnitRole.Pick, PackagingLevel.Case, pickFace)
  def shipCreated() =
    HandlingUnit.Created(id, HandlingUnitRole.Ship, PackagingLevel.Each, packStation)

  describe("HandlingUnit"):
    describe("Pick lifecycle"):
      it("fills at pick face, moves to buffer, empties at put-wall"):
        val (inBuffer, _) = pickCreated().moveToBuffer(bufferArea, at)
        val (empty, _) = inBuffer.empty(at)
        assert(empty.isInstanceOf[HandlingUnit.Empty])

    describe("Ship lifecycle"):
      it("packs at station, clears for shipping, ships"):
        val (packed, _) = shipCreated().pack(at)
        val (ready, _) = packed.readyToShip(at)
        val (shipped, _) = ready.ship(at)
        assert(shipped.isInstanceOf[HandlingUnit.Shipped])

    describe("role enforcement"):
      it("moveToBuffer rejects Ship role"):
        assertThrows[IllegalArgumentException]:
          shipCreated().moveToBuffer(bufferArea, at)

      it("pack rejects Pick role"):
        assertThrows[IllegalArgumentException]:
          pickCreated().pack(at)

    describe("location tracking"):
      it("moveToBuffer updates location from pick face to buffer"):
        val (inBuffer, _) = pickCreated().moveToBuffer(bufferArea, at)
        assert(inBuffer.currentLocation == bufferArea)

      it("pack preserves the station location"):
        val (packed, _) = shipCreated().pack(at)
        assert(packed.currentLocation == packStation)

    describe("packaging level"):
      it("carries through the Pick path"):
        val (inBuffer, _) = pickCreated().moveToBuffer(bufferArea, at)
        assert(inBuffer.packagingLevel == PackagingLevel.Case)
        val (empty, _) = inBuffer.empty(at)
        assert(empty.packagingLevel == PackagingLevel.Case)

      it("carries through the Ship path"):
        val (packed, _) = shipCreated().pack(at)
        assert(packed.packagingLevel == PackagingLevel.Each)
        val (ready, _) = packed.readyToShip(at)
        assert(ready.packagingLevel == PackagingLevel.Each)
        val (shipped, _) = ready.ship(at)
        assert(shipped.packagingLevel == PackagingLevel.Each)

    describe("events"):
      it("identify the handling unit for downstream routing"):
        val (_, bufferEvent) = pickCreated().moveToBuffer(bufferArea, at)
        assert(bufferEvent.handlingUnitId == id)
        val (_, packEvent) = shipCreated().pack(at)
        assert(packEvent.handlingUnitId == id)

      it("moveToBuffer records the destination for transport order creation"):
        val (_, event) = pickCreated().moveToBuffer(bufferArea, at)
        assert(event.locationId == bufferArea)
        assert(event.occurredAt == at)
