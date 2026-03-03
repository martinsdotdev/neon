package neon.app

import neon.common.{HandlingUnitId, LocationId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
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
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def completedEvent(
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ) =
    TaskEvent.TaskCompleted(
      taskId,
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      Some(waveId),
      None,
      handlingUnitId,
      sourceLocationId,
      destinationLocationId,
      10,
      10,
      userId,
      at
    )

  describe("RoutingPolicy"):
    it("skips tasks without a handling unit"):
      assert(RoutingPolicy(completedEvent(None), at).isEmpty)

    it("creates a transport order for the handling unit"):
      val (pending, _) = RoutingPolicy(completedEvent(), at).value
      assert(pending.handlingUnitId == handlingUnitId)
      assert(pending.destination == destinationLocationId)

    it("TransportOrderCreated event carries handling unit, destination, and timestamp"):
      val (pending, event) = RoutingPolicy(completedEvent(), at).value
      assert(event.transportOrderId == pending.id)
      assert(event.handlingUnitId == handlingUnitId)
      assert(event.destination == destinationLocationId)
      assert(event.occurredAt == at)
