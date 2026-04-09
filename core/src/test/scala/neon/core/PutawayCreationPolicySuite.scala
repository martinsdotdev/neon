package neon.core

import neon.common.{ContainerId, LotAttributes, OrderId, PackagingLevel, SkuId}
import neon.goodsreceipt.ReceivedLine
import neon.task.TaskType
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class PutawayCreationPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val orderId = OrderId()
  val at = Instant.now()

  def receivedLine(
      skuId: SkuId = skuId,
      quantity: Int = 10,
      packagingLevel: PackagingLevel = PackagingLevel.Each,
      targetContainerId: Option[ContainerId] = None
  ): ReceivedLine =
    ReceivedLine(skuId, quantity, packagingLevel, LotAttributes(), targetContainerId)

  describe("PutawayCreationPolicy"):
    describe("with an empty list"):
      it("produces no tasks"):
        assert(PutawayCreationPolicy(List.empty, orderId, at).isEmpty)

    describe("with a single line"):
      val line = receivedLine()
      val List((planned, event)) = PutawayCreationPolicy(List(line), orderId, at)

      it("assigns Putaway task type to all created tasks"):
        assert(planned.taskType == TaskType.Putaway)

      it("copies SKU ID from received line"):
        assert(planned.skuId == skuId)

      it("copies packaging level from received line"):
        assert(planned.packagingLevel == PackagingLevel.Each)

      it("assigns line quantity as requestedQuantity"):
        assert(planned.requestedQuantity == 10)

      it("copies order ID"):
        assert(planned.orderId == orderId)

      it("sets waveId to None for putaway tasks"):
        assert(planned.waveId == None)

      it("sets parentTaskId to None"):
        assert(planned.parentTaskId == None)

      it("sets handlingUnitId to None"):
        assert(planned.handlingUnitId == None)

      it("TaskCreated event mirrors all fields of the Planned task"):
        assert(event.taskId == planned.id)
        assert(event.taskType == TaskType.Putaway)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == PackagingLevel.Each)
        assert(event.requestedQuantity == 10)
        assert(event.orderId == orderId)
        assert(event.waveId == None)
        assert(event.parentTaskId == None)
        assert(event.handlingUnitId == None)
        assert(event.occurredAt == at)

    describe("with multiple lines"):
      val line1 = receivedLine(quantity = 5, packagingLevel = PackagingLevel.Case)
      val skuId2 = SkuId()
      val line2 = receivedLine(skuId = skuId2, quantity = 20)
      val results = PutawayCreationPolicy(List(line1, line2), orderId, at)

      it("produces one Planned task per received line"):
        assert(results.size == 2)

      it("each task preserves its line's packaging level"):
        val (p1, _) = results(0)
        val (p2, _) = results(1)
        assert(p1.packagingLevel == PackagingLevel.Case)
        assert(p2.packagingLevel == PackagingLevel.Each)

      it("each task preserves its line's SKU"):
        val (p1, _) = results(0)
        val (p2, _) = results(1)
        assert(p1.skuId == skuId)
        assert(p2.skuId == skuId2)
