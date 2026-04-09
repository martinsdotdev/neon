package neon.counttask

import neon.common.{CountTaskId, CycleCountId, LocationId, SkuId, UserId}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class CountTaskSuite extends AnyFunSpec:
  val id = CountTaskId()
  val cycleCountId = CycleCountId()
  val skuId = SkuId()
  val locationId = LocationId()
  val userId = UserId()
  val at = Instant.now()

  def pendingCountTask(expectedQuantity: Int = 50): CountTask.Pending =
    CountTask.Pending(id, cycleCountId, skuId, locationId, expectedQuantity)

  describe("CountTask"):
    describe("creating"):
      it("captures cycle count reference, SKU, and location"):
        val countTask = pendingCountTask()
        assert(countTask.id == id)
        assert(countTask.cycleCountId == cycleCountId)
        assert(countTask.skuId == skuId)
        assert(countTask.locationId == locationId)

      it("captures expected quantity"):
        val countTask = pendingCountTask(expectedQuantity = 100)
        assert(countTask.expectedQuantity == 100)

    describe("assigning"):
      it("transitions from Pending to Assigned"):
        val (assigned, event) = pendingCountTask().assign(userId, at)
        assert(assigned.isInstanceOf[CountTask.Assigned])
        assert(assigned.id == id)
        assert(assigned.assignedTo == userId)

      it("preserves all fields through the transition"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 75).assign(userId, at)
        assert(assigned.cycleCountId == cycleCountId)
        assert(assigned.skuId == skuId)
        assert(assigned.locationId == locationId)
        assert(assigned.expectedQuantity == 75)

      it("emits CountTaskAssigned event with correct fields"):
        val (_, event) = pendingCountTask().assign(userId, at)
        assert(event.countTaskId == id)
        assert(event.userId == userId)
        assert(event.occurredAt == at)

    describe("recording"):
      it("transitions from Assigned to Recorded"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (recorded, event) = assigned.record(48, at)
        assert(recorded.isInstanceOf[CountTask.Recorded])
        assert(recorded.id == id)

      it("captures actual quantity and computes variance"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (recorded, _) = assigned.record(48, at)
        assert(recorded.actualQuantity == 48)
        assert(recorded.variance == -2)

      it("computes positive variance when actual exceeds expected"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (recorded, _) = assigned.record(55, at)
        assert(recorded.variance == 5)

      it("computes zero variance when actual matches expected"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (recorded, _) = assigned.record(50, at)
        assert(recorded.variance == 0)

      it("preserves all fields through the transition"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (recorded, _) = assigned.record(48, at)
        assert(recorded.cycleCountId == cycleCountId)
        assert(recorded.skuId == skuId)
        assert(recorded.locationId == locationId)
        assert(recorded.expectedQuantity == 50)
        assert(recorded.assignedTo == userId)

      it("emits CountTaskRecorded event with quantities and variance"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 50).assign(userId, at)
        val (_, event) = assigned.record(48, at)
        assert(event.countTaskId == id)
        assert(event.actualQuantity == 48)
        assert(event.expectedQuantity == 50)
        assert(event.variance == -2)
        assert(event.occurredAt == at)

    describe("cancelling"):
      it("can be cancelled from Pending"):
        val (cancelled, event) = pendingCountTask().cancel(at)
        assert(cancelled.isInstanceOf[CountTask.Cancelled])
        assert(cancelled.id == id)
        assert(event.countTaskId == id)
        assert(event.occurredAt == at)

      it("can be cancelled from Assigned"):
        val (assigned, _) = pendingCountTask().assign(userId, at)
        val (cancelled, event) = assigned.cancel(at)
        assert(cancelled.isInstanceOf[CountTask.Cancelled])
        assert(cancelled.id == id)
        assert(event.countTaskId == id)

      it("cancellation preserves cycle count reference"):
        val (cancelled, _) = pendingCountTask().cancel(at)
        assert(cancelled.cycleCountId == cycleCountId)

      it("cancellation from Assigned preserves assignedTo"):
        val (assigned, _) = pendingCountTask().assign(userId, at)
        val (cancelled, _) = assigned.cancel(at)
        assert(cancelled.assignedTo == Some(userId))

      it("cancellation from Pending has no assignedTo"):
        val (cancelled, _) = pendingCountTask().cancel(at)
        assert(cancelled.assignedTo.isEmpty)

    describe("variance calculation"):
      it("handles zero actual quantity"):
        val (assigned, _) = pendingCountTask(expectedQuantity = 100).assign(userId, at)
        val (recorded, event) = assigned.record(0, at)
        assert(recorded.variance == -100)
        assert(event.variance == -100)
