package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.task.{TaskEvent, TaskType}
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class TaskProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = TaskProjectionHandler()

  private def withSession(
      f: R2dbcSession => Unit
  ): Unit =
    val connection =
      Mono.from(connectionFactory.create()).block()
    try
      val session = new R2dbcSession(connection)(using
        system.executionContext,
        system
      )
      f(session)
    finally Mono.from(connection.close()).block()

  private def envelope[E](
      event: E,
      persistenceId: String
  ): EventEnvelope[E] =
    new EventEnvelope[E](
      offset = TimestampOffset.Zero,
      persistenceId = persistenceId,
      sequenceNr = 1L,
      eventOption = Some(event),
      timestamp = System.currentTimeMillis(),
      eventMetadata = None,
      entityType = "Task",
      slice = 0
    )

  describe("TaskProjectionHandler") {

    it("should insert into task_by_wave on TaskCreated") {
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
            envelope(event, s"Task|${taskId.value}")
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM task_by_wave WHERE task_id = '${taskId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should insert into task_by_handling_unit on TaskCreated"
    ) {
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
            envelope(event, s"Task|${taskId.value}")
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM task_by_handling_unit WHERE task_id = '${taskId.value}'"
      )
      assert(count == 1L)
    }

    it("should update state to Allocated on TaskAllocated") {
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
            envelope(created, s"Task|${taskId.value}")
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              allocated,
              s"Task|${taskId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM task_by_wave WHERE task_id = '${taskId.value}' AND state = 'Allocated'"
      )
      assert(count == 1L)
    }

    it("should update state to Completed on TaskCompleted") {
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
            envelope(created, s"Task|${taskId.value}")
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              completed,
              s"Task|${taskId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM task_by_wave WHERE task_id = '${taskId.value}' AND state = 'Completed'"
      )
      assert(count == 1L)
    }
  }
