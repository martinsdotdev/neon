package neon.consolidationgroup

import com.typesafe.config.ConfigFactory
import neon.common.{ConsolidationGroupId, OrderId, WaveId, WorkstationId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class ConsolidationGroupActorSuite
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

  private val groupId = ConsolidationGroupId()
  private val waveId = WaveId()
  private val orderId = OrderId()
  private val workstationId = WorkstationId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    ConsolidationGroupActor.Command,
    ConsolidationGroupEvent,
    ConsolidationGroupActor.State
  ](
    system,
    ConsolidationGroupActor(groupId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createGroup(): Unit =
    val (created, event) =
      ConsolidationGroup.create(waveId, List(orderId), at)
    esTestKit.runCommand[StatusReply[Done]](
      ConsolidationGroupActor.Create(created, event, _)
    )

  private def pickGroup(): Unit =
    esTestKit.runCommand[StatusReply[
      ConsolidationGroupActor.PickResponse
    ]](
      ConsolidationGroupActor.Pick(at, _)
    )

  private def readyGroup(): Unit =
    esTestKit.runCommand[StatusReply[
      ConsolidationGroupActor.ReadyForWorkstationResponse
    ]](
      ConsolidationGroupActor.ReadyForWorkstation(at, _)
    )

  private def assignGroup(): Unit =
    esTestKit.runCommand[StatusReply[
      ConsolidationGroupActor.AssignResponse
    ]](
      ConsolidationGroupActor.Assign(workstationId, at, _)
    )

  describe("ConsolidationGroupActor"):
    describe(
      "full lifecycle Created -> Picked -> ReadyForWorkstation -> Assigned -> Completed"
    ):
      it("transitions through all states"):
        createGroup()
        pickGroup()
        readyGroup()
        assignGroup()

        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CompleteResponse
          ]](
            ConsolidationGroupActor.Complete(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.completed
            .isInstanceOf[ConsolidationGroup.Completed]
        )

    describe("Cancel"):
      it("cancels from Created state"):
        createGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CancelResponse
          ]](
            ConsolidationGroupActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.cancelled
            .isInstanceOf[ConsolidationGroup.Cancelled]
        )

      it("cancels from Picked state"):
        createGroup()
        pickGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CancelResponse
          ]](
            ConsolidationGroupActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)

      it("cancels from ReadyForWorkstation state"):
        createGroup()
        pickGroup()
        readyGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CancelResponse
          ]](
            ConsolidationGroupActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)

      it("cancels from Assigned state"):
        createGroup()
        pickGroup()
        readyGroup()
        assignGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CancelResponse
          ]](
            ConsolidationGroupActor.Cancel(at, _)
          )
        assert(result.reply.isSuccess)

      it("rejects Cancel on Completed"):
        createGroup()
        pickGroup()
        readyGroup()
        assignGroup()
        esTestKit.runCommand[StatusReply[
          ConsolidationGroupActor.CompleteResponse
        ]](
          ConsolidationGroupActor.Complete(at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CancelResponse
          ]](
            ConsolidationGroupActor.Cancel(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("invalid transitions"):
      it("rejects Pick on Picked"):
        createGroup()
        pickGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.PickResponse
          ]](
            ConsolidationGroupActor.Pick(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Complete on Created"):
        createGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.CompleteResponse
          ]](
            ConsolidationGroupActor.Complete(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Assign on Created"):
        createGroup()
        val result =
          esTestKit.runCommand[StatusReply[
            ConsolidationGroupActor.AssignResponse
          ]](
            ConsolidationGroupActor.Assign(workstationId, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Assigned state from journal"):
        createGroup()
        pickGroup()
        readyGroup()
        assignGroup()
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[ConsolidationGroup]](
            ConsolidationGroupActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(
          result.reply.get.isInstanceOf[ConsolidationGroup.Assigned]
        )
        val assigned =
          result.reply.get.asInstanceOf[ConsolidationGroup.Assigned]
        assert(assigned.waveId == waveId)
        assert(assigned.workstationId == workstationId)
