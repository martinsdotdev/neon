package neon.consolidationgroup

import com.typesafe.config.ConfigFactory
import neon.common.{ConsolidationGroupId, OrderId, WaveId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoConsolidationGroupRepositorySuite
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
  private val waveId = WaveId()
  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository =
    PekkoConsolidationGroupRepository(system, connectionFactory = null)

  describe("PekkoConsolidationGroupRepository"):
    describe("save and findById"):
      it("persists a Created consolidation group and retrieves it"):
        val (created, event) =
          ConsolidationGroup.create(waveId, List(OrderId()), at)
        repository.save(created, event).futureValue

        val found = repository.findById(created.id).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[ConsolidationGroup.Created])
        assert(found.get.id == created.id)

      it("returns None for a non-existent consolidation group"):
        val result =
          repository.findById(ConsolidationGroupId()).futureValue
        assert(result.isEmpty)

    describe("saveAll"):
      it("persists multiple consolidation groups"):
        val (cg1, event1) =
          ConsolidationGroup.create(waveId, List(OrderId()), at)
        val (cg2, event2) =
          ConsolidationGroup.create(waveId, List(OrderId()), at)

        repository
          .saveAll(List((cg1, event1), (cg2, event2)))
          .futureValue

        assert(repository.findById(cg1.id).futureValue.isDefined)
        assert(repository.findById(cg2.id).futureValue.isDefined)
