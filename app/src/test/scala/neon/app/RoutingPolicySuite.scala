package neon.app

import neon.common.{HandlingUnitId, LocationId, SkuId, TaskId, WaveId}
import neon.task.{TaskEvent, TaskType}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.OptionValues
import java.time.Instant

class RoutingPolicySuite extends AnyFunSpec with OptionValues:
  val taskId = TaskId()
  val skuId = SkuId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val destination = LocationId()
  val at = Instant.now()

  def completedEvent(
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    TaskEvent.TaskCompleted(
      taskId, TaskType.Pick, skuId, Some(waveId), None, handlingUnitId, 10, 10, at
    )

  describe("RoutingPolicy"):
    it("returns None when the task has no handling unit"):
      assert(RoutingPolicy.evaluate(completedEvent(None), destination, at).isEmpty)

    it("creates a transport order for the handling unit"):
      val (pending, _) = RoutingPolicy.evaluate(completedEvent(), destination, at).value
      assert(pending.handlingUnitId == handlingUnitId)
      assert(pending.destination == destination)

    it("emits a TransportOrderCreated event with correct fields"):
      val (pending, event) = RoutingPolicy.evaluate(completedEvent(), destination, at).value
      assert(event.transportOrderId == pending.id)
      assert(event.handlingUnitId == handlingUnitId)
      assert(event.destination == destination)
      assert(event.occurredAt == at)
