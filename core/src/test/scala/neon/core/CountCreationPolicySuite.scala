package neon.core

import neon.common.{
  CountMethod,
  CountTaskId,
  CountType,
  CycleCountId,
  LocationId,
  SkuId,
  WarehouseAreaId
}
import neon.counttask.{CountTask, CountTaskEvent}
import neon.cyclecount.CycleCount
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class CountCreationPolicySuite extends AnyFunSpec:
  val cycleCountId = CycleCountId()
  val warehouseAreaId = WarehouseAreaId()
  val skuId1 = SkuId()
  val skuId2 = SkuId()
  val locationId1 = LocationId()
  val locationId2 = LocationId()
  val at = Instant.now()

  def inProgressCycleCount(
      skuIds: List[SkuId] = List(skuId1, skuId2),
      countType: CountType = CountType.Planned,
      countMethod: CountMethod = CountMethod.Blind
  ): CycleCount.InProgress =
    CycleCount.InProgress(cycleCountId, warehouseAreaId, skuIds, countType, countMethod)

  describe("CountCreationPolicy"):
    describe("when stock positions exist for all SKUs"):
      it("creates one count task per SKU-location pair"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1, skuId2))
        val stockSnapshots = Map(
          (skuId1, locationId1) -> 100,
          (skuId2, locationId2) -> 50
        )
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        assert(results.size == 2)

      it("sets expected quantity from stock snapshot"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map((skuId1, locationId1) -> 75)
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        val (pending, _) = results.head
        assert(pending.expectedQuantity == 75)

      it("sets cycle count reference on each count task"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map((skuId1, locationId1) -> 100)
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        val (pending, _) = results.head
        assert(pending.cycleCountId == cycleCountId)

      it("sets SKU and location on each count task"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map((skuId1, locationId1) -> 100)
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        val (pending, _) = results.head
        assert(pending.skuId == skuId1)
        assert(pending.locationId == locationId1)

      it("CountTaskCreated event mirrors count task fields"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map((skuId1, locationId1) -> 100)
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        val (pending, event) = results.head
        assert(event.countTaskId == pending.id)
        assert(event.cycleCountId == cycleCountId)
        assert(event.skuId == skuId1)
        assert(event.locationId == locationId1)
        assert(event.expectedQuantity == 100)
        assert(event.occurredAt == at)

    describe("when a SKU exists in multiple locations"):
      it("creates a count task for each location"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map(
          (skuId1, locationId1) -> 30,
          (skuId1, locationId2) -> 20
        )
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        assert(results.size == 2)
        val locations = results.map(_._1.locationId).toSet
        assert(locations == Set(locationId1, locationId2))

    describe("when stock snapshot is empty"):
      it("returns an empty list"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map.empty[(SkuId, LocationId), Int]
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        assert(results.isEmpty)

    describe("when stock snapshot contains SKUs not in cycle count"):
      it("only creates tasks for SKUs in the cycle count"):
        val otherSkuId = SkuId()
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map(
          (skuId1, locationId1) -> 100,
          (otherSkuId, locationId2) -> 50
        )
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        assert(results.size == 1)
        assert(results.head._1.skuId == skuId1)

    describe("when stock snapshot has zero quantity"):
      it("still creates a count task with zero expected quantity"):
        val cycleCount = inProgressCycleCount(skuIds = List(skuId1))
        val stockSnapshots = Map((skuId1, locationId1) -> 0)
        val results = CountCreationPolicy(cycleCount, stockSnapshots, at)
        assert(results.size == 1)
        assert(results.head._1.expectedQuantity == 0)
