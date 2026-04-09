package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.SkuId

import java.util.UUID

class R2dbcSkuRepositorySuite extends PostgresContainerSuite:

  private lazy val repo =
    given scala.concurrent.ExecutionContext = system.executionContext
    R2dbcSkuRepository(connectionFactory)

  private val widgetId =
    SkuId(UUID.fromString("019e0000-0004-7000-8000-000000000001"))
  private val pharmaId =
    SkuId(UUID.fromString("019e0000-0004-7000-8000-000000000004"))
  private val unknownId =
    SkuId(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  describe("R2dbcSkuRepository"):
    describe("findById"):
      it("should return a non-lot-managed sku"):
        val result = repo.findById(widgetId).futureValue
        assert(result.isDefined)
        val sku = result.get
        assert(sku.id == widgetId)
        assert(sku.code == "WIDGET-001")
        assert(sku.description == "Standard Widget")
        assert(!sku.lotManaged)

      it("should return a lot-managed sku"):
        val result = repo.findById(pharmaId).futureValue
        assert(result.isDefined)
        val sku = result.get
        assert(sku.id == pharmaId)
        assert(sku.code == "PHARMA-001")
        assert(sku.description == "Pharmaceutical Item A")
        assert(sku.lotManaged)

      it("should return None for an unknown id"):
        val result = repo.findById(unknownId).futureValue
        assert(result.isEmpty)
