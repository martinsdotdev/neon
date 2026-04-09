package neon.handlingunit

import com.typesafe.config.ConfigFactory
import neon.common.{HandlingUnitId, LocationId, OrderId, PackagingLevel}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoHandlingUnitRepositorySuite
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
  private val locationId = LocationId()
  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository =
    PekkoHandlingUnitRepository(system)

  describe("PekkoHandlingUnitRepository"):
    describe("save and findById"):
      it("persists a PickCreated handling unit and retrieves it"):
        val handlingUnitId = HandlingUnitId()
        val pickCreated =
          HandlingUnit.PickCreated(handlingUnitId, PackagingLevel.Each, locationId)
        val event = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          handlingUnitId,
          LocationId(),
          at
        )
        repository.save(pickCreated, event).futureValue

        val found = repository.findById(handlingUnitId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[HandlingUnit.InBuffer])

      it("persists a ShipCreated handling unit"):
        val handlingUnitId = HandlingUnitId()
        val shipCreated = HandlingUnit.ShipCreated(
          handlingUnitId,
          PackagingLevel.Each,
          locationId,
          OrderId()
        )
        val event =
          HandlingUnitEvent.HandlingUnitPacked(handlingUnitId, OrderId(), at)
        repository.save(shipCreated, event).futureValue

        val found = repository.findById(handlingUnitId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[HandlingUnit.Packed])

      it("returns None for a non-existent handling unit"):
        val result =
          repository.findById(HandlingUnitId()).futureValue
        assert(result.isEmpty)

    describe("findByIds"):
      it("returns multiple handling units"):
        val handlingUnitId1 = HandlingUnitId()
        val handlingUnitId2 = HandlingUnitId()
        val pc1 =
          HandlingUnit.PickCreated(handlingUnitId1, PackagingLevel.Each, locationId)
        val pc2 =
          HandlingUnit.PickCreated(handlingUnitId2, PackagingLevel.Each, locationId)
        val event1 = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          handlingUnitId1,
          LocationId(),
          at
        )
        val event2 = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          handlingUnitId2,
          LocationId(),
          at
        )

        repository.save(pc1, event1).futureValue
        repository.save(pc2, event2).futureValue

        val found =
          repository.findByIds(List(handlingUnitId1, handlingUnitId2)).futureValue
        assert(found.size == 2)
