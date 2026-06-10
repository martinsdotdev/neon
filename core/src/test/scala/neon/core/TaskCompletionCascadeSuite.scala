package neon.core

import neon.common.{OrderId, PackagingLevel, TaskId}
import neon.core.TaskCompletionCascade.CascadeState
import neon.stockposition.StockPositionEvent
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.{EitherValues, OptionValues}

class TaskCompletionCascadeSuite
    extends AnyFunSpec
    with OptionValues
    with EitherValues
    with DomainFactories:

  def validateCompletion(
      task: Option[Task],
      taskId: TaskId,
      actualQuantity: Int = 10,
      verified: Boolean = true,
      verificationProfile: VerificationProfile = VerificationProfile.disabled
  ): Either[TaskCompletionError, Task.Assigned] =
    TaskCompletionCascade.validate(
      taskId = taskId,
      task = task,
      actualQuantity = actualQuantity,
      verified = verified,
      verificationProfile = verificationProfile
    )

  def decideCascade(
      assigned: Task.Assigned,
      actualQuantity: Int,
      state: CascadeState = CascadeState.empty
  ): TaskCompletionCascade.Outcome =
    TaskCompletionCascade.decide(
      assigned = assigned,
      actualQuantity = actualQuantity,
      at = at,
      state = state
    )

  describe("TaskCompletionCascade"):
    describe("validation"):
      describe("when task does not exist"):
        it("returns TaskNotFound"):
          val missingId = TaskId()
          val result = validateCompletion(task = None, taskId = missingId)
          assert(result.left.value == TaskCompletionError.TaskNotFound(missingId))

      describe("when task is not Assigned"):
        it("rejects Planned"):
          val (planned, _) = Task.create(
            taskType = TaskType.Pick,
            skuId = skuId,
            packagingLevel = PackagingLevel.Each,
            requestedQuantity = 10,
            orderId = orderId,
            waveId = Some(waveId),
            parentTaskId = None,
            handlingUnitId = None,
            at = at
          )
          val result = validateCompletion(task = Some(planned), taskId = planned.id)
          assert(result.left.value == TaskCompletionError.TaskNotAssigned(planned.id))

        it("rejects Allocated"):
          val allocated = allocatedTask()
          val result = validateCompletion(task = Some(allocated), taskId = allocated.id)
          assert(result.left.value == TaskCompletionError.TaskNotAssigned(allocated.id))

        it("rejects Completed"):
          val (completed, _) = assignedTask().complete(actualQuantity = 10, at = at)
          val result = validateCompletion(task = Some(completed), taskId = completed.id)
          assert(result.left.value == TaskCompletionError.TaskNotAssigned(completed.id))

        it("rejects Cancelled"):
          val (cancelled, _) = assignedTask().cancel(at)
          val result = validateCompletion(task = Some(cancelled), taskId = cancelled.id)
          assert(result.left.value == TaskCompletionError.TaskNotAssigned(cancelled.id))

      describe("when actual quantity is negative"):
        it("returns InvalidActualQuantity"):
          val task = assignedTask()
          val result = validateCompletion(task = Some(task), taskId = task.id, actualQuantity = -1)
          assert(
            result.left.value ==
              TaskCompletionError.InvalidActualQuantity(taskId = task.id, actualQuantity = -1)
          )

      describe("verification gate"):
        val eachRequired = VerificationProfile(Set(PackagingLevel.Each))

        it("passes when verification is required and provided"):
          val task = assignedTask()
          val result = validateCompletion(
            task = Some(task),
            taskId = task.id,
            verified = true,
            verificationProfile = eachRequired
          )
          assert(result.value == task)

        it("returns VerificationRequired when required and not provided"):
          val task = assignedTask()
          val result = validateCompletion(
            task = Some(task),
            taskId = task.id,
            verified = false,
            verificationProfile = eachRequired
          )
          assert(result.left.value == TaskCompletionError.VerificationRequired(task.id))

        it("passes regardless of verified flag when not required"):
          val task = assignedTask()
          val result = validateCompletion(task = Some(task), taskId = task.id, verified = false)
          assert(result.value == task)

        it("does not gate tasks with a non-matching packaging level"):
          val task = assignedTask()
          val palletOnly = VerificationProfile(Set(PackagingLevel.Pallet))
          val result = validateCompletion(
            task = Some(task),
            taskId = task.id,
            verified = false,
            verificationProfile = palletOnly
          )
          assert(result.value == task)

    describe("completing"):
      it("transitions to Completed with requested and actual quantities"):
        val task = assignedTask(requestedQuantity = 10)
        val outcome = decideCascade(assigned = task, actualQuantity = 10)
        assert(outcome.result.completed.id == task.id)
        assert(outcome.result.completed.actualQuantity == 10)
        assert(outcome.result.completed.requestedQuantity == 10)

      it("TaskCompleted event carries task identity, quantities, and timestamp"):
        val task = assignedTask(requestedQuantity = 10)
        val outcome = decideCascade(assigned = task, actualQuantity = 10)
        assert(outcome.result.completedEvent.taskId == task.id)
        assert(outcome.result.completedEvent.actualQuantity == 10)
        assert(outcome.result.completedEvent.requestedQuantity == 10)
        assert(outcome.result.completedEvent.occurredAt == at)

    describe("shortpick cascade"):
      describe("when actual meets requested"):
        it("does not create a replacement task"):
          val task = assignedTask(requestedQuantity = 10)
          val outcome = decideCascade(assigned = task, actualQuantity = 10)
          assert(outcome.result.shortpick.isEmpty)

      describe("when actual is less than requested"):
        it("creates Planned replacement for the unfulfilled remainder"):
          val task = assignedTask(requestedQuantity = 10)
          val outcome = decideCascade(assigned = task, actualQuantity = 7)
          val (replacement, event) = outcome.result.shortpick.value
          assert(replacement.requestedQuantity == 3)
          assert(replacement.parentTaskId.value == task.id)
          assert(event.requestedQuantity == 3)

      describe("when actual is zero"):
        it("creates replacement for the full requested quantity"):
          val task = assignedTask(requestedQuantity = 10)
          val outcome = decideCascade(assigned = task, actualQuantity = 0)
          val (replacement, _) = outcome.result.shortpick.value
          assert(replacement.requestedQuantity == 10)

    describe("routing cascade"):
      describe("when task has a handling unit"):
        it("creates Pending transport order to the destination"):
          val task = assignedTask(handlingUnitId = Some(handlingUnitId))
          val outcome = decideCascade(assigned = task, actualQuantity = 10)
          val (pending, event) = outcome.result.transportOrder.value
          assert(pending.handlingUnitId == handlingUnitId)
          assert(pending.destination == destinationLocationId)
          assert(event.handlingUnitId == handlingUnitId)

      describe("when task has no handling unit"):
        it("does not create a transport order"):
          val task = assignedTask(handlingUnitId = None)
          val outcome = decideCascade(assigned = task, actualQuantity = 10)
          assert(outcome.result.transportOrder.isEmpty)

    describe("wave completion"):
      describe("when all wave tasks are terminal"):
        it("transitions wave to Completed"):
          val task = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty.copy(wave = Some(releasedWave()), waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          val (completedWave, waveEvent) = outcome.result.waveCompletion.value
          assert(completedWave.id == waveId)
          assert(waveEvent.waveId == waveId)

      describe("when open wave tasks remain"):
        it("does not complete the wave"):
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty
            .copy(wave = Some(releasedWave()), waveTasks = List(task, otherTask))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.waveCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips wave completion"):
          val task = assignedTask(waveId = None)
          val outcome = decideCascade(assigned = task, actualQuantity = 10)
          assert(outcome.result.waveCompletion.isEmpty)

      describe("when wave is already Completed"):
        it("skips wave completion"):
          val task = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty.copy(wave = Some(completedWave()), waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.waveCompletion.isEmpty)

      describe("when wave does not exist"):
        it("skips wave completion"):
          val task = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty.copy(waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.waveCompletion.isEmpty)

      describe("when shortpick creates a replacement"):
        it("prevents wave completion"):
          val task = assignedTask(requestedQuantity = 10, waveId = Some(waveId))
          val state = CascadeState.empty.copy(wave = Some(releasedWave()), waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 7, state = state)
          assert(outcome.result.shortpick.isDefined)
          assert(outcome.result.waveCompletion.isEmpty)

      describe("when the loaded set still contains the completing task pre-completion"):
        it("substitutes the Completed state and completes the wave"):
          val task = assignedTask(waveId = Some(waveId))
          // A fresh in-memory read and a stale projection-backed read both return the task in
          // its Assigned state; the cascade must not let that block wave completion.
          val state = CascadeState.empty.copy(wave = Some(releasedWave()), waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.waveCompletion.isDefined)

      describe("when the loaded set misses the completing task entirely"):
        it("still decides over the completed task"):
          val task = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty.copy(wave = Some(releasedWave()), waveTasks = Nil)
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.waveCompletion.isDefined)

    describe("picking completion"):
      describe("when all consolidation group tasks are terminal"):
        it("transitions consolidation group to Picked"):
          val task = assignedTask(waveId = Some(waveId))
          val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          val (picked, groupEvent) = outcome.result.pickingCompletion.value
          assert(picked.id == group.id)
          assert(groupEvent.consolidationGroupId == group.id)

      describe("when open consolidation group tasks remain"):
        it("does not transition consolidation group"):
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(waveId = Some(waveId))
          val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task, otherTask),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.pickingCompletion.isEmpty)

      describe("when task has no wave"):
        it("skips picking completion"):
          val task = assignedTask(waveId = None)
          val outcome = decideCascade(assigned = task, actualQuantity = 10)
          assert(outcome.result.pickingCompletion.isEmpty)

      describe("when no consolidation group exists for the wave"):
        it("skips picking completion"):
          val task = assignedTask(waveId = Some(waveId))
          val state = CascadeState.empty.copy(wave = Some(releasedWave()), waveTasks = List(task))
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.pickingCompletion.isEmpty)

      describe("when consolidation group is already Picked"):
        it("skips picking completion"):
          val task = assignedTask(waveId = Some(waveId))
          val (picked, _) = createdConsolidationGroup(waveId = waveId).pick(at)
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task),
            consolidationGroups = List(picked)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.pickingCompletion.isEmpty)

      describe("when consolidation group does not contain the task's order"):
        it("skips picking completion"):
          val task = assignedTask(waveId = Some(waveId))
          val group =
            createdConsolidationGroup(waveId = waveId, orderIds = List(OrderId()))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.pickingCompletion.isEmpty)

      describe("when wave has tasks for multiple orders"):
        it("transitions consolidation group independently of other orders' tasks"):
          val otherOrderId = OrderId()
          val task = assignedTask(waveId = Some(waveId))
          val otherTask = assignedTask(orderId = otherOrderId, waveId = Some(waveId))
          val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task, otherTask),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(
            outcome.result.waveCompletion.isEmpty,
            "wave should NOT complete — otherTask still open"
          )
          val (picked, _) = outcome.result.pickingCompletion.value
          assert(picked.id == group.id)

    describe("full cascade"):
      describe("when all conditions are met"):
        it("fires completion, routing, wave, and picking completion"):
          val task = assignedTask(
            requestedQuantity = 10,
            waveId = Some(waveId),
            handlingUnitId = Some(handlingUnitId)
          )
          val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
          assert(outcome.result.completed.actualQuantity == 10)
          assert(outcome.result.shortpick.isEmpty)
          assert(outcome.result.transportOrder.isDefined)
          assert(outcome.result.waveCompletion.isDefined)
          assert(outcome.result.pickingCompletion.isDefined)

      describe("when shortpick occurs"):
        it("prevents wave and consolidation group completion"):
          val task = assignedTask(
            requestedQuantity = 10,
            waveId = Some(waveId),
            handlingUnitId = Some(handlingUnitId)
          )
          val group = createdConsolidationGroup(waveId = waveId, orderIds = List(orderId))
          val state = CascadeState.empty.copy(
            wave = Some(releasedWave()),
            waveTasks = List(task),
            consolidationGroups = List(group)
          )
          val outcome = decideCascade(assigned = task, actualQuantity = 7, state = state)
          assert(outcome.result.shortpick.isDefined)
          assert(outcome.result.transportOrder.isDefined)
          assert(outcome.result.waveCompletion.isEmpty)
          assert(outcome.result.pickingCompletion.isEmpty)

    describe("stock writes"):
      it("consumes allocated stock on full pick"):
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        val state = CascadeState.empty.copy(stockPosition = Some(stockPosition))
        val outcome = decideCascade(assigned = task, actualQuantity = 10, state = state)
        val (updatedPosition, event) = outcome.stockWrites.head
        assert(outcome.stockWrites.size == 1)
        assert(event.isInstanceOf[StockPositionEvent.AllocatedConsumed])
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 90)
        assert(outcome.result.stockConsumption == outcome.stockWrites.lastOption)

      it("consumes actual and deallocates remainder in order on partial pick"):
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        val state = CascadeState.empty.copy(stockPosition = Some(stockPosition))
        val outcome = decideCascade(assigned = task, actualQuantity = 7, state = state)
        assert(outcome.stockWrites.size == 2)
        assert(outcome.stockWrites.head._2.isInstanceOf[StockPositionEvent.AllocatedConsumed])
        assert(outcome.stockWrites.last._2.isInstanceOf[StockPositionEvent.Deallocated])
        val (finalPosition, _) = outcome.stockWrites.last
        // 7 consumed from allocated, 3 deallocated back to available
        assert(finalPosition.allocatedQuantity == 0)
        assert(finalPosition.onHandQuantity == 93)
        assert(finalPosition.availableQuantity == 93)
        assert(outcome.result.stockConsumption == outcome.stockWrites.lastOption)

      it("deallocates the full quantity when actual quantity is zero"):
        val stockPosition = allocatedStockPosition(allocatedQuantity = 10, availableQuantity = 90)
        val task = assignedTask(requestedQuantity = 10, stockPositionId = Some(stockPosition.id))
        val state = CascadeState.empty.copy(stockPosition = Some(stockPosition))
        val outcome = decideCascade(assigned = task, actualQuantity = 0, state = state)
        assert(outcome.stockWrites.size == 1)
        assert(outcome.stockWrites.head._2.isInstanceOf[StockPositionEvent.Deallocated])
        val (updatedPosition, _) = outcome.stockWrites.head
        // All 10 deallocated back to available, nothing consumed
        assert(updatedPosition.allocatedQuantity == 0)
        assert(updatedPosition.onHandQuantity == 100)
        assert(updatedPosition.availableQuantity == 100)
        assert(outcome.result.stockConsumption.isDefined)

      it("produces no writes when no stock position is loaded"):
        val task = assignedTask(requestedQuantity = 10)
        val outcome = decideCascade(assigned = task, actualQuantity = 10)
        assert(outcome.stockWrites.isEmpty)
        assert(outcome.result.stockConsumption.isEmpty)
