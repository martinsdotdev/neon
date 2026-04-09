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
        val huId = HandlingUnitId()
        val pickCreated =
          HandlingUnit.PickCreated(huId, PackagingLevel.Each, locationId)
        val event = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          huId,
          LocationId(),
          at
        )
        repository.save(pickCreated, event).futureValue

        val found = repository.findById(huId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[HandlingUnit.InBuffer])

      it("persists a ShipCreated handling unit"):
        val huId = HandlingUnitId()
        val shipCreated = HandlingUnit.ShipCreated(
          huId,
          PackagingLevel.Each,
          locationId,
          OrderId()
        )
        val event =
          HandlingUnitEvent.HandlingUnitPacked(huId, OrderId(), at)
        repository.save(shipCreated, event).futureValue

        val found = repository.findById(huId).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[HandlingUnit.Packed])

      it("returns None for a non-existent handling unit"):
        val result =
          repository.findById(HandlingUnitId()).futureValue
        assert(result.isEmpty)

    describe("findByIds"):
      it("returns multiple handling units"):
        val huId1 = HandlingUnitId()
        val huId2 = HandlingUnitId()
        val pc1 =
          HandlingUnit.PickCreated(huId1, PackagingLevel.Each, locationId)
        val pc2 =
          HandlingUnit.PickCreated(huId2, PackagingLevel.Each, locationId)
        val event1 = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          huId1,
          LocationId(),
          at
        )
        val event2 = HandlingUnitEvent.HandlingUnitMovedToBuffer(
          huId2,
          LocationId(),
          at
        )

        repository.save(pc1, event1).futureValue
        repository.save(pc2, event2).futureValue

        val found =
          repository.findByIds(List(huId1, huId2)).futureValue
        assert(found.size == 2)
