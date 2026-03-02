package neon.app

import neon.common.{GroupId, HandlingUnitId, LocationId, OrderId, PackagingLevel, WaveId}
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

  def pickedGroup() = ConsolidationGroup.Picked(GroupId(), waveId, orderIds)

  def inBuffer() =
    HandlingUnit.InBuffer(HandlingUnitId(), PackagingLevel.Case, bufferLocation)

  def pickCreated() =
    HandlingUnit.PickCreated(HandlingUnitId(), PackagingLevel.Case, pickFace)

  describe("BufferCompletionPolicy"):
    describe("when all handling units are in buffer"):
      it("transitions the group to ready for workstation"):
        val hus = List(inBuffer(), inBuffer())
        val group = pickedGroup()
        val (ready, event) = BufferCompletionPolicy.evaluate(hus, group, at).value
        assert(ready.id == group.id)
        assert(event.groupId == group.id)

      it("carries waveId and occurredAt in the event"):
        val hus = List(inBuffer())
        val (_, event) = BufferCompletionPolicy.evaluate(hus, pickedGroup(), at).value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

    describe("when some handling units are not yet in buffer"):
      it("does not transition the group"):
        val hus = List(inBuffer(), pickCreated())
        assert(BufferCompletionPolicy.evaluate(hus, pickedGroup(), at).isEmpty)

    describe("when the handling unit list is empty"):
      it("does not transition the group"):
        assert(BufferCompletionPolicy.evaluate(List.empty, pickedGroup(), at).isEmpty)
