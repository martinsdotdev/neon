package neon.cyclecount

import neon.common.{CountMethod, CountType, CycleCountId, SkuId, WarehouseAreaId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class CycleCountSuite extends AnyFunSpec:
  val id = CycleCountId()
  val warehouseAreaId = WarehouseAreaId()
  val skuIds = List(SkuId(), SkuId(), SkuId())
  val at = Instant.now()

  def newCycleCount(
      countType: CountType = CountType.Planned,
      countMethod: CountMethod = CountMethod.Blind
  ): CycleCount.New =
    CycleCount.New(id, warehouseAreaId, skuIds, countType, countMethod)

  describe("CycleCount"):
    describe("creating"):
      it("captures warehouse area and SKUs"):
        val cycleCount = newCycleCount()
        assert(cycleCount.id == id)
        assert(cycleCount.warehouseAreaId == warehouseAreaId)
        assert(cycleCount.skuIds == skuIds)

      it("captures count type and method"):
        val cycleCount = newCycleCount(CountType.Random, CountMethod.Informed)
        assert(cycleCount.countType == CountType.Random)
        assert(cycleCount.countMethod == CountMethod.Informed)

    describe("starting"):
      it("transitions from New to InProgress"):
        val (inProgress, event) = newCycleCount().start(at)
        assert(inProgress.isInstanceOf[CycleCount.InProgress])
        assert(inProgress.id == id)

      it("preserves all fields through the transition"):
        val (inProgress, _) = newCycleCount(CountType.Triggered, CountMethod.Informed).start(at)
        assert(inProgress.warehouseAreaId == warehouseAreaId)
        assert(inProgress.skuIds == skuIds)
        assert(inProgress.countType == CountType.Triggered)
        assert(inProgress.countMethod == CountMethod.Informed)

      it("emits CycleCountStarted event with correct fields"):
        val (_, event) = newCycleCount().start(at)
        assert(event.cycleCountId == id)
        assert(event.occurredAt == at)

    describe("completing"):
      it("transitions from InProgress to Completed"):
        val (inProgress, _) = newCycleCount().start(at)
        val (completed, event) = inProgress.complete(at)
        assert(completed.isInstanceOf[CycleCount.Completed])
        assert(completed.id == id)
        assert(event.cycleCountId == id)
        assert(event.occurredAt == at)

      it("preserves all fields through the transition"):
        val (inProgress, _) = newCycleCount(CountType.Recount, CountMethod.Blind).start(at)
        val (completed, _) = inProgress.complete(at)
        assert(completed.warehouseAreaId == warehouseAreaId)
        assert(completed.skuIds == skuIds)
        assert(completed.countType == CountType.Recount)
        assert(completed.countMethod == CountMethod.Blind)

    describe("cancelling"):
      it("can be cancelled from New"):
        val (cancelled, event) = newCycleCount().cancel(at)
        assert(cancelled.isInstanceOf[CycleCount.Cancelled])
        assert(cancelled.id == id)
        assert(event.cycleCountId == id)
        assert(event.occurredAt == at)

      it("can be cancelled from InProgress"):
        val (inProgress, _) = newCycleCount().start(at)
        val (cancelled, event) = inProgress.cancel(at)
        assert(cancelled.isInstanceOf[CycleCount.Cancelled])
        assert(cancelled.id == id)
        assert(event.cycleCountId == id)

      it("cancellation preserves count type and method"):
        val (cancelled, _) = newCycleCount(CountType.Random, CountMethod.Informed).cancel(at)
        assert(cancelled.countType == CountType.Random)
        assert(cancelled.countMethod == CountMethod.Informed)

    describe("count type propagation"):
      it("carries count type through all events"):
        val cycleCount = newCycleCount(CountType.Triggered)
        val (_, startEvent) = cycleCount.start(at)
        assert(startEvent.countType == CountType.Triggered)
        val (inProgress, _) = cycleCount.start(at)
        val (_, completeEvent) = inProgress.complete(at)
        assert(completeEvent.countType == CountType.Triggered)
