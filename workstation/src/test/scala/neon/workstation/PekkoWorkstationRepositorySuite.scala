package neon.workstation

import com.typesafe.config.ConfigFactory
import neon.common.WorkstationId
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoWorkstationRepositorySuite
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
    PekkoWorkstationRepository(system, connectionFactory = null)

  describe("PekkoWorkstationRepository"):
    describe("create and findById"):
      it("persists a Disabled workstation via create and retrieves it"):
        val wsId = WorkstationId()
        val disabled =
          Workstation.Disabled(wsId, WorkstationType.PutWall, 8)
        repository.create(disabled).futureValue

        val found = repository.findById(wsId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[Workstation.Disabled])
        assert(found.get.id == wsId)

      it("returns None for a non-existent workstation"):
        val result =
          repository.findById(WorkstationId()).futureValue
        assert(result.isEmpty)

    describe("save with enable"):
      it("transitions Disabled to Idle"):
        val wsId = WorkstationId()
        val disabled =
          Workstation.Disabled(wsId, WorkstationType.PutWall, 8)
        repository.create(disabled).futureValue

        val (idle, event) = disabled.enable(at)
        repository.save(idle, event).futureValue

        val found = repository.findById(wsId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[Workstation.Idle])
