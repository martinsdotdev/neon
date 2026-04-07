package neon.task

import neon.common.{
  HandlingUnitId,
  LocationId,
  OrderId,
  PackagingLevel,
  SkuId,
  TaskId,
  UserId,
  WaveId
}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.concurrent.duration.*

class PekkoTaskRepositorySuite
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
  private val skuId = SkuId()
  private val orderId = OrderId()
  private val waveId = WaveId()
  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  private lazy val repository = PekkoTaskRepository(system)

  private def createTask(): (Task.Planned, TaskEvent.TaskCreated) =
    Task.create(
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      10,
      orderId,
      Some(waveId),
      None,
      None,
      at
    )

  describe("PekkoTaskRepository"):
    describe("save and findById"):
      it("persists a Planned task and retrieves it"):
        val (planned, event) = createTask()
        repository.save(planned, event).futureValue

        val found = repository.findById(planned.id).futureValue
        assert(found.isDefined)
        assert(found.get.isInstanceOf[Task.Planned])
        assert(found.get.id == planned.id)

      it("returns None for a non-existent task"):
        val result = repository.findById(TaskId()).futureValue
        assert(result.isEmpty)

    describe("saveAll"):
      it("persists multiple tasks"):
        val (task1, event1) = createTask()
        val (task2, event2) = createTask()

        repository
          .saveAll(List((task1, event1), (task2, event2)))
          .futureValue

        assert(repository.findById(task1.id).futureValue.isDefined)
        assert(repository.findById(task2.id).futureValue.isDefined)
