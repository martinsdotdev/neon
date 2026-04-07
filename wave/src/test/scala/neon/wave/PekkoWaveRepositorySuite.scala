package neon.wave

import com.typesafe.config.ConfigFactory
import neon.common.{OrderId, WaveId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoWaveRepositorySuite
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
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  private given org.apache.pekko.util.Timeout = 5.seconds

  private val at = Instant.now()
  private val cluster = Cluster(system)

  // Form a single-node cluster for sharding
  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository = PekkoWaveRepository(system)

  describe("PekkoWaveRepository"):
    describe("save and findById"):
      it("persists a Released wave via Create command and retrieves it"):
        val waveId = WaveId()
        val orderIds = List(OrderId(), OrderId())
        val released = Wave.Released(waveId, OrderGrouping.Single, orderIds)
        val event =
          WaveEvent.WaveReleased(waveId, OrderGrouping.Single, orderIds, at)

        repository.save(released, event).futureValue

        val found = repository.findById(waveId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[Wave.Released])
        assert(found.get.id == waveId)

      it("returns None for a non-existent wave"):
        val result = repository.findById(WaveId()).futureValue
        assert(result.isEmpty)
