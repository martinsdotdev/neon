package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.task.{TaskEvent, TaskType}

import java.time.Instant

class TaskProjectionHandlerSuite extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = TaskProjectionHandler()

  describe("TaskProjectionHandler"):

    it("inserts into task_by_wave on TaskCreated"):
      val taskId = TaskId()
      val waveId = WaveId()
      val orderId = OrderId()
      val handlingUnitId = HandlingUnitId()

      val event = TaskEvent.TaskCreated(
        taskId = taskId,
        taskType = TaskType.Pick,
        skuId = SkuId(),
        packagingLevel = PackagingLevel.Case,
        orderId = orderId,
        waveId = Some(waveId),
        parentTaskId = None,
        handlingUnitId = Some(handlingUnitId),
        requestedQuantity = 10,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(event, s"Task|${taskId.value}", "Task")
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM task_by_wave " +
          s"WHERE task_id = '${taskId.value}'"
      )
      assert(count == 1L)

    it("inserts into task_by_handling_unit on TaskCreated"):
      val taskId = TaskId()
      val waveId = WaveId()
      val orderId = OrderId()
      val handlingUnitId = HandlingUnitId()

      val event = TaskEvent.TaskCreated(
        taskId = taskId,
        taskType = TaskType.Pick,
        skuId = SkuId(),
        packagingLevel = PackagingLevel.Case,
        orderId = orderId,
        waveId = Some(waveId),
        parentTaskId = None,
        handlingUnitId = Some(handlingUnitId),
        requestedQuantity = 5,
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(event, s"Task|${taskId.value}", "Task")
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM task_by_handling_unit " +
          s"WHERE task_id = '${taskId.value}'"
      )
      assert(count == 1L)

    it("updates state to Allocated on TaskAllocated"):
      val taskId = TaskId()
      val waveId = WaveId()
      val orderId = OrderId()

      val created = TaskEvent.TaskCreated(
        taskId = taskId,
        taskType = TaskType.Pick,
        skuId = SkuId(),
        packagingLevel = PackagingLevel.Case,
        orderId = orderId,
        waveId = Some(waveId),
        parentTaskId = None,
        handlingUnitId = None,
        requestedQuantity = 3,
        occurredAt = Instant.now()
      )

      val allocated = TaskEvent.TaskAllocated(
        taskId = taskId,
        taskType = TaskType.Pick,
        sourceLocationId = LocationId(),
        destinationLocationId = LocationId(),
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"Task|${taskId.value}",
              "Task"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              allocated,
              s"Task|${taskId.value}",
              "Task"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM task_by_wave " +
          s"WHERE task_id = '${taskId.value}' " +
          "AND state = 'Allocated'"
      )
      assert(count == 1L)

    it("updates state to Completed on TaskCompleted"):
      val taskId = TaskId()
      val waveId = WaveId()
      val orderId = OrderId()

      val created = TaskEvent.TaskCreated(
        taskId = taskId,
        taskType = TaskType.Pick,
        skuId = SkuId(),
        packagingLevel = PackagingLevel.Case,
        orderId = orderId,
        waveId = Some(waveId),
        parentTaskId = None,
        handlingUnitId = None,
        requestedQuantity = 3,
        occurredAt = Instant.now()
      )

      val completed = TaskEvent.TaskCompleted(
        taskId = taskId,
        taskType = TaskType.Pick,
        skuId = SkuId(),
        packagingLevel = PackagingLevel.Case,
        waveId = Some(waveId),
        parentTaskId = None,
        handlingUnitId = None,
        sourceLocationId = LocationId(),
        destinationLocationId = LocationId(),
        requestedQuantity = 3,
        actualQuantity = 3,
        assignedTo = UserId(),
        occurredAt = Instant.now()
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"Task|${taskId.value}",
              "Task"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              completed,
              s"Task|${taskId.value}",
              "Task"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM task_by_wave " +
          s"WHERE task_id = '${taskId.value}' " +
          "AND state = 'Completed'"
      )
      assert(count == 1L)
