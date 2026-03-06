package neon.app

import neon.common.{ConsolidationGroupId, HandlingUnitId, LocationId, OrderId, PackagingLevel, WaveId}
import neon.consolidationgroup.ConsolidationGroup
import neon.handlingunit.HandlingUnit
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class BufferCompletionPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val orderIds = List(OrderId(), OrderId())
  val bufferLocation = LocationId()
  val pickFace = LocationId()
  val at = Instant.now()

  def pickedConsolidationGroup() = ConsolidationGroup.Picked(ConsolidationGroupId(), waveId, orderIds)

  def inBuffer() =
    HandlingUnit.InBuffer(HandlingUnitId(), PackagingLevel.Case, bufferLocation)

  def pickCreated() =
    HandlingUnit.PickCreated(HandlingUnitId(), PackagingLevel.Case, pickFace)

  def empty() =
    HandlingUnit.Empty(HandlingUnitId(), PackagingLevel.Case)

  describe("BufferCompletionPolicy"):
    describe("when all handling units are in buffer"):
      it("transitions the consolidation group to ready for workstation"):
        val handlingUnits = List(inBuffer(), inBuffer())
        val consolidationGroup = pickedConsolidationGroup()
        val (ready, event) = BufferCompletionPolicy(handlingUnits, consolidationGroup, at).value
        assert(ready.id == consolidationGroup.id)
        assert(event.consolidationGroupId == consolidationGroup.id)

      it("carries waveId and occurredAt in the event"):
        val handlingUnits = List(inBuffer())
        val (_, event) =
          BufferCompletionPolicy(handlingUnits, pickedConsolidationGroup(), at).value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("preserves consolidation group identity across transition"):
        val consolidationGroup = pickedConsolidationGroup()
        val handlingUnits = List(inBuffer())
        val (ready, _) = BufferCompletionPolicy(handlingUnits, consolidationGroup, at).value
        assert(ready.waveId == waveId)
        assert(ready.orderIds == orderIds)

    describe("when some handling units are not yet in buffer"):
      it("does not transition when pick handling units are still in transit"):
        val handlingUnits = List(inBuffer(), pickCreated())
        assert(BufferCompletionPolicy(handlingUnits, pickedConsolidationGroup(), at).isEmpty)

      it("does not transition when handling units have already been emptied"):
        val handlingUnits = List(inBuffer(), empty())
        assert(BufferCompletionPolicy(handlingUnits, pickedConsolidationGroup(), at).isEmpty)

    describe("when the handling unit list is empty"):
      it("does not transition the consolidation group"):
        assert(BufferCompletionPolicy(List.empty, pickedConsolidationGroup(), at).isEmpty)
