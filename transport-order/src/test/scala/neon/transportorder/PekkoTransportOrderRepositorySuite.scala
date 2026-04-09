package neon.transportorder

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, LocationId, TransportOrderId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoTransportOrderRepositorySuite
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
  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository =
    PekkoTransportOrderRepository(system, connectionFactory = null)

  describe("PekkoTransportOrderRepository"):
    describe("save and findById"):
      it("persists a Pending transport order and retrieves it"):
        val (pending, event) =
          TransportOrder.create(HandlingUnitId(), LocationId(), at)
        repository.save(pending, event).futureValue

        val found = repository.findById(pending.id).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[TransportOrder.Pending])
        assert(found.get.id == pending.id)

      it("returns None for a non-existent transport order"):
        val result =
          repository.findById(TransportOrderId()).futureValue
        assert(result.isEmpty)

    describe("saveAll"):
      it("persists multiple transport orders"):
        val (to1, event1) =
          TransportOrder.create(HandlingUnitId(), LocationId(), at)
        val (to2, event2) =
          TransportOrder.create(HandlingUnitId(), LocationId(), at)

        repository
          .saveAll(List((to1, event1), (to2, event2)))
          .futureValue

        assert(repository.findById(to1.id).futureValue.isDefined)
        assert(repository.findById(to2.id).futureValue.isDefined)
