package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.{CarrierId, OrderId, PackagingLevel, Priority, SkuId}

import java.util.UUID

class R2dbcOrderRepositorySuite extends PostgresContainerSuite:

  private lazy val repo =
    given scala.concurrent.ExecutionContext = system.executionContext
    R2dbcOrderRepository(connectionFactory)

  private val fedexOrder1Id =
    OrderId(UUID.fromString("019e0000-0005-7000-8000-000000000001"))
  private val fedexOrder2Id =
    OrderId(UUID.fromString("019e0000-0005-7000-8000-000000000002"))
  private val upsOrder1Id =
    OrderId(UUID.fromString("019e0000-0005-7000-8000-000000000004"))
  private val fedexCarrierId =
    CarrierId(UUID.fromString("019e0000-0002-7000-8000-000000000001"))
  private val widget1SkuId =
    SkuId(UUID.fromString("019e0000-0004-7000-8000-000000000001"))
  private val unknownId =
    OrderId(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  describe("R2dbcOrderRepository"):
    describe("findById"):
      it("should return an order with multiple lines"):
        val result = repo.findById(fedexOrder1Id).futureValue
        assert(result.isDefined)
        val order = result.get
        assert(order.id == fedexOrder1Id)
        assert(order.priority == Priority.Normal)
        assert(order.carrierId.contains(fedexCarrierId))
        assert(order.lines.size == 2)
        val caseLine =
          order.lines.find(_.packagingLevel == PackagingLevel.Case)
        assert(caseLine.isDefined)
        assert(caseLine.get.skuId == widget1SkuId)
        assert(caseLine.get.quantity == 3)

      it("should return a high priority order with a single line"):
        val result = repo.findById(fedexOrder2Id).futureValue
        assert(result.isDefined)
        val order = result.get
        assert(order.priority == Priority.High)
        assert(order.lines.size == 1)

      it("should return None for an unknown id"):
        val result = repo.findById(unknownId).futureValue
        assert(result.isEmpty)

    describe("findByIds"):
      it("should return multiple orders"):
        val ids = List(fedexOrder1Id, upsOrder1Id)
        val result = repo.findByIds(ids).futureValue
        assert(result.size == 2)
        assert(result.map(_.id).toSet == ids.toSet)

      it("should return an empty list for empty input"):
        val result = repo.findByIds(Nil).futureValue
        assert(result.isEmpty)

      it("should return only existing orders when some ids are unknown"):
        val ids = List(fedexOrder1Id, unknownId)
        val result = repo.findByIds(ids).futureValue
        assert(result.size == 1)
        assert(result.head.id == fedexOrder1Id)
