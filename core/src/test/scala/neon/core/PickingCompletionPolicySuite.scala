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
      id = TaskId(),
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = 10,
      actualQuantity = 10,
      orderId = orderId,
      waveId = Some(waveId),
      parentTaskId = None,
      handlingUnitId = None,
      stockPositionId = None,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId,
      assignedTo = UserId()
    )

  def cancelledTask() =
    Task.Cancelled(
      id = TaskId(),
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      orderId = orderId,
      waveId = Some(waveId),
      parentTaskId = None,
      handlingUnitId = None,
      stockPositionId = None,
      sourceLocationId = Some(sourceLocationId),
      destinationLocationId = Some(destinationLocationId),
      assignedTo = None
    )

  def assignedTask() =
    Task.Assigned(
      id = TaskId(),
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = 10,
      orderId = orderId,
      waveId = Some(waveId),
      parentTaskId = None,
      handlingUnitId = None,
      stockPositionId = None,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId,
      assignedTo = UserId()
    )

  def allocatedTask() =
    Task.Allocated(
      id = TaskId(),
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = 10,
      orderId = orderId,
      waveId = Some(waveId),
      parentTaskId = None,
      handlingUnitId = None,
      stockPositionId = None,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId
    )

  def plannedTask() =
    Task.Planned(
      id = TaskId(),
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = 10,
      orderId = orderId,
      waveId = Some(waveId),
      parentTaskId = None,
      handlingUnitId = None
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
