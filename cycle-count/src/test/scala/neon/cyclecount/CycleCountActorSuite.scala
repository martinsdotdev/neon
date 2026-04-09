package neon.cyclecount

import com.typesafe.config.ConfigFactory
import neon.common.{CountMethod, CountType, CycleCountId, SkuId, WarehouseAreaId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class CycleCountActorSuite
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

  private val cycleCountId = CycleCountId()
  private val warehouseAreaId = WarehouseAreaId()
  private val skuIds = List(SkuId(), SkuId())
  private val at = Instant.now()

  private val serializationSettings = EventSourcedBehaviorTestKit.SerializationSettings.disabled
    .withVerifyEvents(true)
    .withVerifyState(true)

  private val esTestKit = EventSourcedBehaviorTestKit[
    CycleCountActor.Command,
    CycleCountEvent,
    CycleCountActor.State
  ](
    system,
    CycleCountActor(cycleCountId.value.toString),
    serializationSettings
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createCycleCount(): Unit =
    val newCount =
      CycleCount.New(cycleCountId, warehouseAreaId, skuIds, CountType.Planned, CountMethod.Blind)
    val event = CycleCountEvent.CycleCountCreated(
      cycleCountId,
      warehouseAreaId,
      skuIds,
      CountType.Planned,
      CountMethod.Blind,
      at
    )
    esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
      CycleCountActor.Create(newCount, event, _)
    )

  describe("CycleCountActor"):
    describe("Create"):
      it("persists CycleCountCreated event and sets New state"):
        val newCount = CycleCount.New(
          cycleCountId,
          warehouseAreaId,
          skuIds,
          CountType.Planned,
          CountMethod.Blind
        )
        val event = CycleCountEvent.CycleCountCreated(
          cycleCountId,
          warehouseAreaId,
          skuIds,
          CountType.Planned,
          CountMethod.Blind,
          at
        )
        val result = esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
          CycleCountActor.Create(newCount, event, _)
        )
        assert(result.event == event)
        assert(
          result
            .stateOfType[CycleCountActor.ActiveState]
            .cycleCount
            .isInstanceOf[CycleCount.New]
        )

    describe("GetState"):
      it("returns None when empty"):
        val result =
          esTestKit.runCommand[Option[CycleCount]](CycleCountActor.GetState(_))
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createCycleCount()
        val result =
          esTestKit.runCommand[Option[CycleCount]](CycleCountActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CycleCount.New])

    describe("Start"):
      it("transitions New to InProgress"):
        createCycleCount()
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.StartResponse]](
              CycleCountActor.Start(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.inProgress.isInstanceOf[CycleCount.InProgress])
        assert(response.event.cycleCountId == cycleCountId)
        assert(
          result
            .stateOfType[CycleCountActor.ActiveState]
            .cycleCount
            .isInstanceOf[CycleCount.InProgress]
        )

      it("rejects Start in EmptyState"):
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.StartResponse]](
              CycleCountActor.Start(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Complete"):
      it("transitions InProgress to Completed"):
        createCycleCount()
        esTestKit.runCommand[StatusReply[CycleCountActor.StartResponse]](
          CycleCountActor.Start(at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.CompleteResponse]](
              CycleCountActor.Complete(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.completed.isInstanceOf[CycleCount.Completed])
        assert(response.event.cycleCountId == cycleCountId)
        assert(
          result
            .stateOfType[CycleCountActor.ActiveState]
            .cycleCount
            .isInstanceOf[CycleCount.Completed]
        )

      it("rejects Complete on New"):
        createCycleCount()
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.CompleteResponse]](
              CycleCountActor.Complete(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Cancel"):
      it("cancels a New cycle count"):
        createCycleCount()
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.CancelResponse]](
              CycleCountActor.Cancel(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.cancelled.isInstanceOf[CycleCount.Cancelled])
        assert(
          result
            .stateOfType[CycleCountActor.ActiveState]
            .cycleCount
            .isInstanceOf[CycleCount.Cancelled]
        )

      it("cancels an InProgress cycle count"):
        createCycleCount()
        esTestKit.runCommand[StatusReply[CycleCountActor.StartResponse]](
          CycleCountActor.Start(at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.CancelResponse]](
              CycleCountActor.Cancel(at, _)
            )
        assert(result.reply.isSuccess)
        assert(
          result
            .stateOfType[CycleCountActor.ActiveState]
            .cycleCount
            .isInstanceOf[CycleCount.Cancelled]
        )

      it("rejects Cancel on Completed"):
        createCycleCount()
        esTestKit.runCommand[StatusReply[CycleCountActor.StartResponse]](
          CycleCountActor.Start(at, _)
        )
        esTestKit.runCommand[StatusReply[CycleCountActor.CompleteResponse]](
          CycleCountActor.Complete(at, _)
        )
        val result =
          esTestKit
            .runCommand[StatusReply[CycleCountActor.CancelResponse]](
              CycleCountActor.Cancel(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers New state from journal"):
        createCycleCount()
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[CycleCount]](CycleCountActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CycleCount.New])

      it("recovers InProgress state from journal"):
        createCycleCount()
        esTestKit.runCommand[StatusReply[CycleCountActor.StartResponse]](
          CycleCountActor.Start(at, _)
        )
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[CycleCount]](CycleCountActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[CycleCount.InProgress])
