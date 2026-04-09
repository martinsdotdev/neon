package neon.inventory

import com.typesafe.config.ConfigFactory
import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoInventoryRepositorySuite
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
    PekkoInventoryRepository(system, connectionFactory = null)

  describe("PekkoInventoryRepository"):
    describe("save and findById"):
      it("persists inventory via create and retrieves it"):
        val (inventory, event) = Inventory.create(
          LocationId(),
          SkuId(),
          PackagingLevel.Each,
          Some(Lot("LOT-001")),
          100,
          at
        )
        repository.save(inventory, event).futureValue

        val found = repository.findById(inventory.id).futureValue
        assert(found.isDefined)
        assert(found.get.onHand == 100)
        assert(found.get.reserved == 0)

      it("returns None for a non-existent inventory"):
        val result =
          repository.findById(InventoryId()).futureValue
        assert(result.isEmpty)

    describe("save with reserve"):
      it("updates reserved quantity"):
        val (inventory, createEvent) = Inventory.create(
          LocationId(),
          SkuId(),
          PackagingLevel.Each,
          None,
          50,
          at
        )
        repository.save(inventory, createEvent).futureValue

        val (updated, reserveEvent) = inventory.reserve(20, at)
        repository.save(updated, reserveEvent).futureValue

        val found = repository.findById(inventory.id).futureValue
        assert(found.isDefined)
        assert(found.get.onHand == 50)
        assert(found.get.reserved == 20)
