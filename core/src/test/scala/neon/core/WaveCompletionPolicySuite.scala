package neon.core

import neon.common.{LocationId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
import neon.task.{Task, TaskType}
import neon.wave.{OrderGrouping, Wave}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class WaveCompletionPolicySuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderId = OrderId()
  val orderIds = List(OrderId(), OrderId())
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def released() = Wave.Released(waveId, OrderGrouping.Multi, orderIds)

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

  describe("WaveCompletionPolicy"):
    describe("when all tasks are terminal"):
      it("transitions wave to Completed"):
        val tasks = List(completedTask(), completedTask(), completedTask())
        val (_, event) = WaveCompletionPolicy(tasks, released(), at).value
        assert(event.waveId == waveId)

      it("considers Cancelled tasks terminal"):
        val tasks = List(completedTask(), cancelledTask(), completedTask())
        assert(WaveCompletionPolicy(tasks, released(), at).isDefined)

    describe("when tasks are still open"):
      it("does not complete when Assigned tasks remain"):
        val tasks = List(completedTask(), assignedTask(), completedTask())
        assert(WaveCompletionPolicy(tasks, released(), at).isEmpty)

      it("considers Allocated tasks non-terminal"):
        val tasks = List(completedTask(), allocatedTask())
        assert(WaveCompletionPolicy(tasks, released(), at).isEmpty)

      it("considers Planned tasks non-terminal"):
        val tasks = List(completedTask(), plannedTask())
        assert(WaveCompletionPolicy(tasks, released(), at).isEmpty)

    describe("when the task list is empty"):
      it("does not complete for empty task list"):
        assert(WaveCompletionPolicy(List.empty, released(), at).isEmpty)
