package neon.app

import neon.common.{GroupId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
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
  val at = Instant.now()

  def createdGroup() = ConsolidationGroup.Created(GroupId(), waveId, orderIds)

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
      UserId()
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
      it("transitions group to Picked state"):
        val tasks = List(completedTask(), completedTask())
        val group = createdGroup()
        val (picked, event) = PickingCompletionPolicy(tasks, group, at).value
        assert(picked.id == group.id)
        assert(event.groupId == group.id)

      it("considers Cancelled tasks terminal"):
        val tasks = List(completedTask(), cancelledTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isDefined)

      it("ConsolidationGroupPicked event includes waveId and occurredAt"):
        val tasks = List(completedTask())
        val (_, event) = PickingCompletionPolicy(tasks, createdGroup(), at).value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("Picked state retains group waveId and orderIds"):
        val group = createdGroup()
        val tasks = List(completedTask())
        val (picked, _) = PickingCompletionPolicy(tasks, group, at).value
        assert(picked.waveId == waveId)
        assert(picked.orderIds == orderIds)

    describe("when tasks are still open"):
      it("returns None when non-terminal tasks remain"):
        val tasks = List(completedTask(), assignedTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isEmpty)

      it("considers Planned tasks non-terminal"):
        val tasks = List(completedTask(), plannedTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isEmpty)

    describe("when the task list is empty"):
      it("returns None for empty task list"):
        assert(PickingCompletionPolicy(List.empty, createdGroup(), at).isEmpty)
