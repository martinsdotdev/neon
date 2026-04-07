package neon.wave

import neon.common.{OrderId, WaveId}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant

class WaveActorSuite
    extends ScalaTestWithActorTestKit(
      EventSourcedBehaviorTestKit.config.withFallback(
        ConfigFactory.parseString("pekko.actor.provider = local")
      )
    )
    with AnyFunSpecLike
    with BeforeAndAfterEach:

  private val waveId = WaveId()
  private val orderIds = List(OrderId(), OrderId())
  private val at = Instant.now()

  private val esTestKit = EventSourcedBehaviorTestKit[
    WaveActor.Command,
    WaveEvent,
    WaveActor.State
  ](
    system,
    WaveActor(waveId.value.toString),
    EventSourcedBehaviorTestKit.SerializationSettings.disabled
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    esTestKit.clear()

  private def createWave(): Unit =
    val planned = Wave.Planned(waveId, OrderGrouping.Single, orderIds)
    val event =
      WaveEvent.WaveReleased(waveId, OrderGrouping.Single, orderIds, at)
    esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
      WaveActor.Create(planned, event, _)
    )

  describe("WaveActor"):
    describe("Create"):
      it("persists WaveReleased event and sets Released state"):
        val planned =
          Wave.Planned(waveId, OrderGrouping.Single, orderIds)
        val event = WaveEvent.WaveReleased(
          waveId,
          OrderGrouping.Single,
          orderIds,
          at
        )
        val result =
          esTestKit.runCommand[StatusReply[org.apache.pekko.Done]](
            WaveActor.Create(planned, event, _)
          )
        assert(result.event == event)
        assert(
          result
            .stateOfType[WaveActor.ActiveState]
            .wave
            .isInstanceOf[Wave.Released]
        )

    describe("GetState"):
      it("returns None when empty"):
        val result =
          esTestKit.runCommand[Option[Wave]](WaveActor.GetState(_))
        assert(result.reply.isEmpty)

      it("returns Some after creation"):
        createWave()
        val result =
          esTestKit.runCommand[Option[Wave]](WaveActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Wave.Released])

    describe("Complete"):
      it("transitions Released to Completed"):
        createWave()
        val result =
          esTestKit
            .runCommand[StatusReply[WaveActor.CompleteResponse]](
              WaveActor.Complete(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.completed.isInstanceOf[Wave.Completed])
        assert(response.event.waveId == waveId)
        assert(
          result
            .stateOfType[WaveActor.ActiveState]
            .wave
            .isInstanceOf[Wave.Completed]
        )

      it("rejects Complete in EmptyState"):
        val result =
          esTestKit
            .runCommand[StatusReply[WaveActor.CompleteResponse]](
              WaveActor.Complete(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("Cancel"):
      it("cancels a Released wave"):
        createWave()
        val result =
          esTestKit
            .runCommand[StatusReply[WaveActor.CancelResponse]](
              WaveActor.Cancel(at, _)
            )
        assert(result.reply.isSuccess)
        val response = result.reply.getValue
        assert(response.cancelled.isInstanceOf[Wave.Cancelled])
        assert(
          result
            .stateOfType[WaveActor.ActiveState]
            .wave
            .isInstanceOf[Wave.Cancelled]
        )

      it("rejects Cancel on Completed wave"):
        createWave()
        esTestKit
          .runCommand[StatusReply[WaveActor.CompleteResponse]](
            WaveActor.Complete(at, _)
          )
        val result =
          esTestKit
            .runCommand[StatusReply[WaveActor.CancelResponse]](
              WaveActor.Cancel(at, _)
            )
        assert(result.reply.isError)
        assert(result.hasNoEvents)

    describe("event replay"):
      it("recovers Released state from journal"):
        createWave()
        esTestKit.restart()
        val result =
          esTestKit.runCommand[Option[Wave]](WaveActor.GetState(_))
        assert(result.reply.isDefined)
        assert(result.reply.get.isInstanceOf[Wave.Released])
