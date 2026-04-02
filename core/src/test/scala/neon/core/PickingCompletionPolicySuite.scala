package neon.core

import neon.common.{
  ConsolidationGroupId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  UserId,
  WaveId
}
import neon.consolidationgroup.ConsolidationGroup
import neon.task.{Task, TaskType}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class PickingCompletionPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderId = OrderId()
  val orderIds = List(OrderId(), OrderId())
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def createdConsolidationGroup() =
    ConsolidationGroup.Created(ConsolidationGroupId(), waveId, orderIds)

  def completedTask() =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      10,
      orderId,
      Some(waveId),
      None,
      None,
      sourceLocationId,
      destinationLocationId,
      UserId()
    )

  def cancelledTask() =
    Task.Cancelled(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      orderId,
      Some(waveId),
      None,
      None,
      Some(sourceLocationId),
      Some(destinationLocationId),
      None
    )

  def assignedTask() =
    Task.Assigned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      None,
      sourceLocationId,
      destinationLocationId,
      UserId()
    )

  def allocatedTask() =
    Task.Allocated(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      None,
      sourceLocationId,
      destinationLocationId
    )

  def plannedTask() =
    Task.Planned(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      None
    )

  describe("PickingCompletionPolicy"):
    describe("when all tasks are terminal"):
      it("transitions consolidation group to Picked state"):
        val tasks = List(completedTask(), completedTask())
        val consolidationGroup = createdConsolidationGroup()
        val (picked, event) = PickingCompletionPolicy(tasks, consolidationGroup, at).value
        assert(picked.id == consolidationGroup.id)
        assert(event.consolidationGroupId == consolidationGroup.id)

      it("considers Cancelled tasks terminal"):
        val tasks = List(completedTask(), cancelledTask())
        assert(PickingCompletionPolicy(tasks, createdConsolidationGroup(), at).isDefined)

      it("ConsolidationGroupPicked event carries wave context and timestamp"):
        val tasks = List(completedTask())
        val (_, event) = PickingCompletionPolicy(tasks, createdConsolidationGroup(), at).value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("Picked state retains consolidation group waveId and orderIds"):
        val consolidationGroup = createdConsolidationGroup()
        val tasks = List(completedTask())
        val (picked, _) = PickingCompletionPolicy(tasks, consolidationGroup, at).value
        assert(picked.waveId == waveId)
        assert(picked.orderIds == orderIds)

    describe("when tasks are still open"):
      it("does not transition when Assigned tasks remain"):
        val tasks = List(completedTask(), assignedTask())
        assert(PickingCompletionPolicy(tasks, createdConsolidationGroup(), at).isEmpty)

      it("considers Allocated tasks non-terminal"):
        val tasks = List(completedTask(), allocatedTask())
        assert(PickingCompletionPolicy(tasks, createdConsolidationGroup(), at).isEmpty)

      it("considers Planned tasks non-terminal"):
        val tasks = List(completedTask(), plannedTask())
        assert(PickingCompletionPolicy(tasks, createdConsolidationGroup(), at).isEmpty)

    describe("when the task list is empty"):
      it("does not transition for empty task list"):
        assert(PickingCompletionPolicy(List.empty, createdConsolidationGroup(), at).isEmpty)
