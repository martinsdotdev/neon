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
      it("produces no tasks"):
        assert(TaskCreationPolicy(List.empty, at).isEmpty)

    describe("with a single request"):
      val req = request()
      val List((planned, event)) = TaskCreationPolicy(List(req), at)

      it("assigns Pick task type to all created tasks"):
        assert(planned.taskType == TaskType.Pick)

      it("copies SKU ID from request"):
        assert(planned.skuId == skuId)

      it("copies packaging level from request"):
        assert(planned.packagingLevel == PackagingLevel.Each)

      it("assigns request quantity as requestedQty"):
        assert(planned.requestedQty == 5)

      it("copies order ID from request"):
        assert(planned.orderId == orderId)

      it("carries wave ID from the request"):
        assert(planned.waveId == Some(waveId))

      it("sets parentTaskId to None"):
        assert(planned.parentTaskId == None)

      it("sets handlingUnitId to None"):
        assert(planned.handlingUnitId == None)

      it("TaskCreated event mirrors all fields of the Planned task"):
        assert(event.taskId == planned.id)
        assert(event.taskType == TaskType.Pick)
        assert(event.skuId == skuId)
        assert(event.packagingLevel == PackagingLevel.Each)
        assert(event.requestedQty == 5)
        assert(event.orderId == orderId)
        assert(event.waveId == Some(waveId))
        assert(event.parentTaskId == None)
        assert(event.handlingUnitId == None)
        assert(event.occurredAt == at)

    describe("with multiple requests"):
      val req1 = request(PackagingLevel.Case, 3)
      val req2 = request(PackagingLevel.Each, 10)
      val results = TaskCreationPolicy(List(req1, req2), at)

      it("produces one Planned task per TaskRequest"):
        assert(results.size == 2)

      it("all tasks share the same wave ID"):
        assert(results.forall(_._1.waveId == Some(waveId)))

      it("each task preserves its request's packaging level"):
        val (p1, _) = results(0)
        val (p2, _) = results(1)
        assert(p1.packagingLevel == PackagingLevel.Case)
        assert(p2.packagingLevel == PackagingLevel.Each)
