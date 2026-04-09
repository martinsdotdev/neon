package neon.workstation

import com.typesafe.config.ConfigFactory
import neon.common.{ConsolidationGroupId, WorkstationMode, WorkstationId}
import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class WorkstationActorSuite
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

  private val workstationId = WorkstationId()
  private val consolidationGroupId = ConsolidationGroupId()
  private val at = Instant.now()

  private val serializationSettings =
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
      .withVerifyEvents(true)
      .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    WorkstationActor.Command,
    WorkstationActor.ActorEvent,
    WorkstationActor.State
  ](
    system,
    WorkstationActor(workstationId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createDisabled(): Unit =
    val workstation =
      Workstation.Disabled(workstationId, WorkstationType.PutWall, 8)
    esTestKit.runCommand[StatusReply[Done]](
      WorkstationActor.Create(workstation, _)
    )

  private def enableWorkstation(): Unit =
    esTestKit.runCommand[StatusReply[
      WorkstationActor.EnableResponse
    ]](
      WorkstationActor.Enable(at, _)
    )

  describe("WorkstationActor"):
    describe("full lifecycle Disabled -> Idle -> Active -> Idle -> Disabled"):
      it("transitions through all states"):
        createDisabled()
        enableWorkstation()

        val assignResult =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.AssignResponse
          ]](
            WorkstationActor.Assign(consolidationGroupId.value, at, _)
          )
        assert(assignResult.reply.isSuccess)
        assert(
          assignResult.reply.getValue.active
            .isInstanceOf[Workstation.Active]
        )

        val releaseResult =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.ReleaseResponse
          ]](
            WorkstationActor.Release(at, _)
          )
        assert(releaseResult.reply.isSuccess)
        assert(
          releaseResult.reply.getValue.idle
            .isInstanceOf[Workstation.Idle]
        )

        val disableResult =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.DisableResponse
          ]](
            WorkstationActor.Disable(at, _)
          )
        assert(disableResult.reply.isSuccess)
        assert(
          disableResult.reply.getValue.disabled
            .isInstanceOf[Workstation.Disabled]
        )

    describe("SwitchMode"):
      it("switches mode on Idle workstation"):
        createDisabled()
        enableWorkstation()

        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.SwitchModeResponse
          ]](
            WorkstationActor.SwitchMode(
              WorkstationMode.Receiving,
              at,
              _
            )
          )
        assert(result.reply.isSuccess)
        assert(
          result.reply.getValue.idle.mode == WorkstationMode.Receiving
        )

      it("rejects SwitchMode on Disabled"):
        createDisabled()
        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.SwitchModeResponse
          ]](
            WorkstationActor.SwitchMode(
              WorkstationMode.Counting,
              at,
              _
            )
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects SwitchMode on Active"):
        createDisabled()
        enableWorkstation()
        esTestKit.runCommand[StatusReply[
          WorkstationActor.AssignResponse
        ]](
          WorkstationActor.Assign(consolidationGroupId.value, at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.SwitchModeResponse
          ]](
            WorkstationActor.SwitchMode(
              WorkstationMode.Counting,
              at,
              _
            )
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Disable from Active"):
      it("allows direct disable from Active state"):
        createDisabled()
        enableWorkstation()
        esTestKit.runCommand[StatusReply[
          WorkstationActor.AssignResponse
        ]](
          WorkstationActor.Assign(consolidationGroupId.value, at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.DisableResponse
          ]](
            WorkstationActor.Disable(at, _)
          )
        assert(result.reply.isSuccess)

    describe("idempotent Create"):
      it("acks on second Create without error"):
        createDisabled()
        val workstation =
          Workstation.Disabled(workstationId, WorkstationType.PutWall, 8)
        val result = esTestKit.runCommand[StatusReply[Done]](
          WorkstationActor.Create(workstation, _)
        )
        assert(result.reply.isSuccess)
        assert(result.hasNoEvents)

    describe("invalid transitions"):
      it("rejects Enable on Idle"):
        createDisabled()
        enableWorkstation()
        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.EnableResponse
          ]](
            WorkstationActor.Enable(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Assign on Disabled"):
        createDisabled()
        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.AssignResponse
          ]](
            WorkstationActor.Assign(consolidationGroupId.value, at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Assign on Active"):
        createDisabled()
        enableWorkstation()
        esTestKit.runCommand[StatusReply[
          WorkstationActor.AssignResponse
        ]](
          WorkstationActor.Assign(consolidationGroupId.value, at, _)
        )

        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.AssignResponse
          ]](
            WorkstationActor.Assign(
              ConsolidationGroupId().value,
              at,
              _
            )
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

      it("rejects Release on Idle"):
        createDisabled()
        enableWorkstation()
        val result =
          esTestKit.runCommand[StatusReply[
            WorkstationActor.ReleaseResponse
          ]](
            WorkstationActor.Release(at, _)
          )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Active state from journal"):
        createDisabled()
        enableWorkstation()
        esTestKit.runCommand[StatusReply[
          WorkstationActor.AssignResponse
        ]](
          WorkstationActor.Assign(consolidationGroupId.value, at, _)
        )
        esTestKit.restart()

        val result =
          esTestKit.runCommand[Option[Workstation]](
            WorkstationActor.GetState(_)
          )
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Workstation.Active])
        val active = result.reply.get.asInstanceOf[Workstation.Active]
        assert(active.assignmentId == consolidationGroupId.value)
        assert(active.workstationType == WorkstationType.PutWall)
