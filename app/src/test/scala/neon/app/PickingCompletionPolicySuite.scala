package neon.app

import neon.common.{GroupId, OrderId, SkuId, TaskId, UserId, WaveId}
import neon.consolidationgroup.ConsolidationGroup
import neon.task.{Task, TaskType}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class PickingCompletionPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderIds = List(OrderId(), OrderId())
  val at = Instant.now()

  def createdGroup() = ConsolidationGroup.Created(GroupId(), waveId, orderIds)

  def completedTask() =
    Task.Completed(TaskId(), TaskType.Pick, skuId, 10, 10, Some(waveId), None, None, UserId())

  def cancelledTask() =
    Task.Cancelled(TaskId(), TaskType.Pick, skuId, Some(waveId), None, None, None)

  def assignedTask() =
    Task.Assigned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, None, UserId())

  def plannedTask() =
    Task.Planned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, None)

  describe("PickingCompletionPolicy"):
    describe("when all tasks are terminal"):
      it("picks the consolidation group"):
        val tasks = List(completedTask(), completedTask())
        val group = createdGroup()
        val (picked, event) = PickingCompletionPolicy(tasks, group, at).value
        assert(picked.id == group.id)
        assert(event.groupId == group.id)

      it("treats cancelled tasks as terminal"):
        val tasks = List(completedTask(), cancelledTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isDefined)

      it("carries waveId and occurredAt in the event"):
        val tasks = List(completedTask())
        val (_, event) = PickingCompletionPolicy(tasks, createdGroup(), at).value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

      it("preserves group identity across transition"):
        val group = createdGroup()
        val tasks = List(completedTask())
        val (picked, _) = PickingCompletionPolicy(tasks, group, at).value
        assert(picked.waveId == waveId)
        assert(picked.orderIds == orderIds)

    describe("when tasks are still open"):
      it("does not pick the group"):
        val tasks = List(completedTask(), assignedTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isEmpty)

      it("treats planned tasks as non-terminal"):
        val tasks = List(completedTask(), plannedTask())
        assert(PickingCompletionPolicy(tasks, createdGroup(), at).isEmpty)

    describe("when the task list is empty"):
      it("does not pick the group"):
        assert(PickingCompletionPolicy(List.empty, createdGroup(), at).isEmpty)
