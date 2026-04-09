package neon.counttask

import com.typesafe.config.ConfigFactory
import neon.common.{CountTaskId, CycleCountId, LocationId, SkuId, UserId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class CountTaskActorSuite
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

  private val countTaskId = CountTaskId()
  private val cycleCountId = CycleCountId()
  private val skuId = SkuId()
  private val locationId = LocationId()
  private val userId = UserId()
  private val at = Instant.now()

  private val serializationSettings = EventSourcedBehaviorTestKit.SerializationSettings.disabled
    .withVerifyEvents(true)
    .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    CountTaskActor.Command,
    CountTaskEvent,
    CountTaskActor.State
  ](
    system,
    CountTaskActor(countTaskId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createCountTask(): Unit =
    val pending = CountTask.Pending(countTaskId, cycleCountId, skuId, locationId, 50)
    val event = CountTaskEvent.CountTaskCreated(
      countTaskId,
      cycleCountId,
      skuId,
      locationId,
      50,
      at
    )
    esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
      CountTaskActor.Create(pending, event, _)
    )

  describe("CountTaskActor"):
    describe("Create"):
      it("persists CountTaskCreated event and sets Pending state"):
        val pending = CountTask.Pending(countTaskId, cycleCountId, skuId, locationId, 50)
        val event = CountTaskEvent.CountTaskCreated(
          countTaskId,
          cycleCountId,
          skuId,
          locationId,
          50,
          at
        )
        val result = esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
          CountTaskActor.Create(pending, event, _)
        )
        assert(result.event == event)
        assert(
          result
            .stateOfType[CountTaskActor.ActiveState]
            .countTask
            .isInstanceOf[CountTask.Pending]
        )

    describe("GetState"):
      it("returns None when empty"):
        val result =
          esTestKit.runCommand[Option[CountTask]](CountTaskActor.GetState(_))
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createCountTask()
        val result =
          esTestKit.runCommand[Option[CountTask]](CountTaskActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CountTask.Pending])

    describe("Assign"):
      it("transitions Pending to Assigned"):
        createCountTask()
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.AssignResponse]](
              CountTaskActor.Assign(userId, at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.assigned.isInstanceOf[CountTask.Assigned])
        assert(response.assigned.assignedTo == userId)
        assert(response.event.countTaskId == countTaskId)
        assert(
          result
            .stateOfType[CountTaskActor.ActiveState]
            .countTask
            .isInstanceOf[CountTask.Assigned]
        )

      it("rejects Assign in EmptyState"):
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.AssignResponse]](
              CountTaskActor.Assign(userId, at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Record"):
      it("transitions Assigned to Recorded"):
        createCountTask()
        esTestKit.runCommand[StatusReply[CountTaskActor.AssignResponse]](
          CountTaskActor.Assign(userId, at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.RecordResponse]](
              CountTaskActor.Record(48, at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.recorded.isInstanceOf[CountTask.Recorded])
        assert(response.recorded.actualQuantity == 48)
        assert(response.recorded.variance == -2)
        assert(response.event.countTaskId == countTaskId)
        assert(
          result
            .stateOfType[CountTaskActor.ActiveState]
            .countTask
            .isInstanceOf[CountTask.Recorded]
        )

      it("rejects Record on Pending"):
        createCountTask()
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.RecordResponse]](
              CountTaskActor.Record(48, at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Cancel"):
      it("cancels a Pending count task"):
        createCountTask()
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.CancelResponse]](
              CountTaskActor.Cancel(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.cancelled.isInstanceOf[CountTask.Cancelled])
        assert(
          result
            .stateOfType[CountTaskActor.ActiveState]
            .countTask
            .isInstanceOf[CountTask.Cancelled]
        )

      it("cancels an Assigned count task"):
        createCountTask()
        esTestKit.runCommand[StatusReply[CountTaskActor.AssignResponse]](
          CountTaskActor.Assign(userId, at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.CancelResponse]](
              CountTaskActor.Cancel(at, _)
            )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[CountTaskActor.ActiveState]
            .countTask
            .isInstanceOf[CountTask.Cancelled]
        )

      it("rejects Cancel on Recorded"):
        createCountTask()
        esTestKit.runCommand[StatusReply[CountTaskActor.AssignResponse]](
          CountTaskActor.Assign(userId, at, _)
        )
        esTestKit.runCommand[StatusReply[CountTaskActor.RecordResponse]](
          CountTaskActor.Record(48, at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CountTaskActor.CancelResponse]](
              CountTaskActor.Cancel(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Pending state from journal"):
        createCountTask()
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[CountTask]](CountTaskActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CountTask.Pending])

      it("recovers Assigned state from journal"):
        createCountTask()
        esTestKit.runCommand[StatusReply[CountTaskActor.AssignResponse]](
          CountTaskActor.Assign(userId, at, _)
        )
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[CountTask]](CountTaskActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CountTask.Assigned])
