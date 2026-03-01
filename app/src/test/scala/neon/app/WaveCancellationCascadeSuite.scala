package neon.app

import neon.common.{OrderId, SkuId, TaskId, UserId, WaveId}
import neon.task.{Task, TaskType}
import neon.wave.{OrderGrouping, Wave}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.OptionValues
import java.time.Instant

class WaveCancellationCascadeSuite extends AnyFunSpec with OptionValues:
  val waveId = WaveId()
  val skuId = SkuId()
  val orderIds = List(OrderId(), OrderId())
  val at = Instant.now()

  def released() = Wave.Released(waveId, OrderGrouping.Multi, orderIds)

  def plannedTask() =
    Task.Planned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None)

  def assignedTask() =
    Task.Assigned(TaskId(), TaskType.Pick, skuId, 10, Some(waveId), None, UserId())

  def completedTask() =
    Task.Completed(TaskId(), TaskType.Pick, skuId, 10, 10, Some(waveId))

  /** Applies the cancellation cascade: cancel open tasks, then check wave completion. */
  def cascade(wave: Wave.Released, tasks: List[Task], at: Instant) =
    val (cancelledWave, waveCancelledEvent) = wave.cancel(at)
    val cancelledResults = TaskCancellationPolicy.evaluate(tasks, at)
    val cancelledIds = cancelledResults.map(_._1.id).toSet
    val updatedTasks = tasks.map:
      case t if cancelledIds.contains(t.id) =>
        cancelledResults.find(_._1.id == t.id).get._1
      case t => t
    val waveCompleted = WaveCompletionPolicy.evaluate(updatedTasks, wave, at)
    (cancelledWave, waveCancelledEvent, cancelledResults, waveCompleted)

  describe("WaveCancellationCascade"):
    describe("full cascade"):
      it("cancelling a released wave completes it after all tasks are cancelled"):
        val wave = released()
        val tasks = List(plannedTask(), assignedTask())
        val (_, _, _, waveCompleted) = cascade(wave, tasks, at)
        assert(waveCompleted.isDefined)

      it("produces TaskCancelled events for each open task"):
        val wave = released()
        val tasks = List(plannedTask(), assignedTask())
        val (_, _, cancelledResults, _) = cascade(wave, tasks, at)
        assert(cancelledResults.size == 2)
        cancelledResults.foreach: (cancelled, event) =>
          assert(event.taskId == cancelled.id)
          assert(event.occurredAt == at)

      it("produces a WaveCompleted event at the end"):
        val wave = released()
        val tasks = List(plannedTask(), assignedTask())
        val (_, _, _, waveCompleted) = cascade(wave, tasks, at)
        val (_, event) = waveCompleted.value
        assert(event.waveId == waveId)
        assert(event.occurredAt == at)

    describe("partial completion before cancellation"):
      it("skips already-completed tasks and still completes the wave"):
        val wave = released()
        val tasks = List(completedTask(), assignedTask(), completedTask())
        val (_, _, cancelledResults, waveCompleted) = cascade(wave, tasks, at)
        assert(cancelledResults.size == 1)
        assert(waveCompleted.isDefined)

    describe("edge: all tasks already completed"):
      it("wave is already completable without cancellation"):
        val wave = released()
        val tasks = List(completedTask(), completedTask())
        val (_, _, cancelledResults, waveCompleted) = cascade(wave, tasks, at)
        assert(cancelledResults.isEmpty)
        assert(waveCompleted.isDefined)
