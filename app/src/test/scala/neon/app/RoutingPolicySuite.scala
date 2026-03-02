package neon.app

import neon.common.{HandlingUnitId, LocationId, SkuId, TaskId, UserId, WaveId}
import neon.task.{TaskEvent, TaskType}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class RoutingPolicySuite extends AnyFunSpec with OptionValues:
  val taskId = TaskId()
  val skuId = SkuId()
  val userId = UserId()
  val waveId = WaveId()
  val handlingUnitId = HandlingUnitId()
  val destination = LocationId()
  val at = Instant.now()

  def completedEvent(
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    TaskEvent.TaskCompleted(
      taskId,
      TaskType.Pick,
      skuId,
      Some(waveId),
      None,
      handlingUnitId,
      10,
      10,
      userId,
      at
    )

  describe("RoutingPolicy"):
    it("skips tasks without a handling unit"):
      assert(RoutingPolicy(completedEvent(None), destination, at).isEmpty)

    it("creates a transport order for the handling unit"):
      val (pending, _) = RoutingPolicy(completedEvent(), destination, at).value
      assert(pending.handlingUnitId == handlingUnitId)
      assert(pending.destination == destination)

    it("emits a TransportOrderCreated event with correct fields"):
      val (pending, event) = RoutingPolicy(completedEvent(), destination, at).value
      assert(event.transportOrderId == pending.id)
      assert(event.handlingUnitId == handlingUnitId)
      assert(event.destination == destination)
      assert(event.occurredAt == at)
