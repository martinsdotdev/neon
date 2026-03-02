package neon.app

import neon.common.{HandlingUnitId, LocationId, PackagingLevel, TransportOrderId}
import neon.handlingunit.HandlingUnit
import neon.transportorder.TransportOrderEvent
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class BufferArrivalPolicySuite extends AnyFunSpec:
  val handlingUnitId = HandlingUnitId()
  val transportOrderId = TransportOrderId()
  val pickFace = LocationId()
  val bufferArea = LocationId()
  val at = Instant.now()

  def confirmed() =
    TransportOrderEvent.TransportOrderConfirmed(transportOrderId, handlingUnitId, bufferArea, at)

  def pickCreated() =
    HandlingUnit.PickCreated(handlingUnitId, PackagingLevel.Case, pickFace)

  describe("BufferArrivalPolicy"):
    it("moves the handling unit to the transport order destination"):
      val (inBuffer, _) = BufferArrivalPolicy(confirmed(), pickCreated(), at)
      assert(inBuffer.currentLocation == bufferArea)

    it("emits an event identifying the handling unit and destination"):
      val (_, event) = BufferArrivalPolicy(confirmed(), pickCreated(), at)
      assert(event.handlingUnitId == handlingUnitId)
      assert(event.locationId == bufferArea)
      assert(event.occurredAt == at)
