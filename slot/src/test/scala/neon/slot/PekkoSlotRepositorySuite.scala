package neon.slot

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, OrderId, SlotId, WorkstationId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoSlotRepositorySuite
    extends ScalaTestWithActorTestKit(
      EventSourcedBehaviorTestKit.config.withFallback(
        ConfigFactory.parseString("""
          pekko.actor.provider = cluster
          pekko.remote.artery.canonical.port = 0
          pekko.cluster.seed-nodes = []
        """)
      )
    )
    with AnyFunSpecLike
    with ScalaFutures
    with Eventually:

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = Span(10, Seconds),
      interval = Span(100, Millis)
    )

  private given org.apache.pekko.util.Timeout = 5.seconds

  private val at = Instant.now()
  private val workstationId = WorkstationId()
  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository =
    PekkoSlotRepository(system, connectionFactory = null)

  describe("PekkoSlotRepository"):
    describe("save and findById"):
      it("persists an Available slot and transitions to Reserved"):
        val slotId = SlotId()
        val available = Slot.Available(slotId, workstationId)
        val orderId = OrderId()
        val huId = HandlingUnitId()
        val (reserved, event) =
          available.reserve(orderId, huId, at)
        repository.save(available, event).futureValue

        val found = repository.findById(slotId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[Slot.Reserved])

      it("returns None for a non-existent slot"):
        val result = repository.findById(SlotId()).futureValue
        assert(result.isEmpty)

    describe("saveAll"):
      it("persists multiple slots"):
        val slotId1 = SlotId()
        val slotId2 = SlotId()
        val available1 = Slot.Available(slotId1, workstationId)
        val available2 = Slot.Available(slotId2, workstationId)
        val (_, event1) =
          available1.reserve(OrderId(), HandlingUnitId(), at)
        val (_, event2) =
          available2.reserve(OrderId(), HandlingUnitId(), at)

        repository
          .saveAll(
            List((available1, event1), (available2, event2))
          )
          .futureValue

        assert(
          repository.findById(slotId1).futureValue.isDefined
        )
        assert(
          repository.findById(slotId2).futureValue.isDefined
        )
