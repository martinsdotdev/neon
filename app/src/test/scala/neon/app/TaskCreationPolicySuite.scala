package neon.app

import neon.common.{OrderId, PackagingLevel, SkuId, WaveId}
import neon.task.TaskType
import neon.wave.TaskRequest
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskCreationPolicySuite extends AnyFunSpec:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderId = OrderId()
  val at = Instant.now()

  def request(
      packagingLevel: PackagingLevel = PackagingLevel.Each,
      quantity: Int = 5
  ) =
    TaskRequest(waveId, orderId, skuId, packagingLevel, quantity)

  describe("TaskCreationPolicy"):
    describe("with an empty list"):
      it("returns an empty list"):
        assert(TaskCreationPolicy(List.empty, at).isEmpty)

    describe("with a single request"):
      val req = request()
      val List((planned, event)) = TaskCreationPolicy(List(req), at)

      it("taskType is always Pick"):
        assert(planned.taskType == TaskType.Pick)

      it("loads skuId from request"):
        assert(planned.skuId == skuId)

      it("loads packagingLevel from request"):
        assert(planned.packagingLevel == PackagingLevel.Each)

      it("loads quantity as requestedQty"):
        assert(planned.requestedQty == 5)

      it("loads waveId from request"):
        assert(planned.waveId == Some(waveId))

      it("parentTaskId is None"):
        assert(planned.parentTaskId == None)

      it("handlingUnitId is None"):
        assert(planned.handlingUnitId == None)

      it("event reflects the Planned state"):
        assert(event.taskId == planned.id)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == PackagingLevel.Each)
        assert(event.requestedQty == 5)
        assert(event.waveId == Some(waveId))
        assert(event.parentTaskId == None)
        assert(event.handlingUnitId == None)
        assert(event.occurredAt == at)

    describe("with multiple requests"):
      val req1 = request(PackagingLevel.Case, 3)
      val req2 = request(PackagingLevel.Each, 10)
      val results = TaskCreationPolicy(List(req1, req2), at)

      it("creates one task per request"):
        assert(results.size == 2)

      it("waveId is consistent across all tasks"):
        assert(results.forall(_._1.waveId == Some(waveId)))

      it("each task carries its own packagingLevel"):
        val (p1, _) = results(0)
        val (p2, _) = results(1)
        assert(p1.packagingLevel == PackagingLevel.Case)
        assert(p2.packagingLevel == PackagingLevel.Each)
