package neon.task

import com.typesafe.config.ConfigFactory
import neon.common.{LocationId, OrderId, PackagingLevel, SkuId, TaskId, UserId, WaveId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class TaskActorSuite
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
          pekko.actor {
            provider = local
            serialization-bindings {
              "neon.common.serialization.CborSerializable" = jackson-cbor
            }
          }
        """)
        .withFallback(EventSourcedBehaviorTestKit.config)
        .resolve()
    )
    with AnyFunSpecLike
    with BeforeAndAfterEach:

  private val taskId = TaskId()
  private val skuId = SkuId()
  private val orderId = OrderId()
  private val waveId = WaveId()
  private val userId = UserId()
  private val srcLoc = LocationId()
  private val dstLoc = LocationId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    TaskActor.Command,
    TaskEvent,
    TaskActor.State
  ](
    system,
    TaskActor(taskId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def plannedTask(): (Task.Planned, TaskEvent.TaskCreated) =
    Task.create(
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      None,
      at
    )

  private def createPlanned(): Unit =
    val (planned, event) = plannedTask()
    esTestKit.runCommand[StatusReply[Done]](
      TaskActor.Create(planned, event, _)
    )

  describe("TaskActor"):
    describe("full lifecycle Planned -> Allocated -> Assigned -> Completed"):
      it("transitions through all states"):
        createPlanned()

        val allocResult =
          esTestKit.runCommand[StatusReply[TaskActor.AllocateResponse]](
            TaskActor.Allocate(srcLoc, dstLoc, at, _)
          )
        assert(allocResult.reply.isSuccess)
        assert(
          allocResult.reply.getValue.allocated.isInstanceOf[Task.Allocated]
        )

        val assignResult =
          esTestKit.runCommand[StatusReply[TaskActor.AssignResponse]](
            TaskActor.Assign(userId, at, _)
          )
        assert(assignResult.reply.isSuccess)
        assert(
          assignResult.reply.getValue.assigned.isInstanceOf[Task.Assigned]
        )

        val completeResult =
          esTestKit.runCommand[StatusReply[TaskActor.CompleteResponse]](
            TaskActor.Complete(10, at, _)
          )
        assert(completeResult.reply.isSuccess)
        val response = completeResult.reply.getValue
        assert(response.completed.actualQuantity == 10)
        assert(response.completed.requestedQuantity == 10)

    describe("Cancel"):
      it("cancels from Planned state"):
        createPlanned()
        val result =
          esTestKit.runCommand[StatusReply[TaskActor.CancelResponse]](
            TaskActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.cancelled.isInstanceOf[Task.Cancelled]
        )

      it("cancels from Assigned state"):
        createPlanned()
        esTestKit.runCommand[StatusReply[TaskActor.AllocateResponse]](
          TaskActor.Allocate(srcLoc, dstLoc, at, _)
        )
        esTestKit.runCommand[StatusReply[TaskActor.AssignResponse]](
          TaskActor.Assign(userId, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[TaskActor.CancelResponse]](
            TaskActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        val cancelled = result.reply.getValue.cancelled
        assert(cancelled.assignedTo.contains(userId))

      it("rejects Cancel on Completed task"):
        createPlanned()
        esTestKit.runCommand[StatusReply[TaskActor.AllocateResponse]](
          TaskActor.Allocate(srcLoc, dstLoc, at, _)
        )
        esTestKit.runCommand[StatusReply[TaskActor.AssignResponse]](
          TaskActor.Assign(userId, at, _)
        )
        esTestKit.runCommand[StatusReply[TaskActor.CompleteResponse]](
          TaskActor.Complete(10, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[TaskActor.CancelResponse]](
            TaskActor.Cancel(at, _)
          )
        assert(result.reply.isError)

    describe("invalid transitions"):
      it("rejects Assign on Planned task"):
        createPlanned()
        val result =
          esTestKit.runCommand[StatusReply[TaskActor.AssignResponse]](
            TaskActor.Assign(userId, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Complete on Allocated task"):
        createPlanned()
        esTestKit.runCommand[StatusReply[TaskActor.AllocateResponse]](
          TaskActor.Allocate(srcLoc, dstLoc, at, _)
        )
        val result =
          esTestKit.runCommand[StatusReply[TaskActor.CompleteResponse]](
            TaskActor.Complete(10, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Assigned state from journal"):
        createPlanned()
        esTestKit.runCommand[StatusReply[TaskActor.AllocateResponse]](
          TaskActor.Allocate(srcLoc, dstLoc, at, _)
        )
        esTestKit.runCommand[StatusReply[TaskActor.AssignResponse]](
          TaskActor.Assign(userId, at, _)
        )
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[Task]](TaskActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Task.Assigned])
        val assigned = result.reply.get.asInstanceOf[Task.Assigned]
        assert(assigned.assignedTo == userId)
        assert(assigned.sourceLocationId == srcLoc)
