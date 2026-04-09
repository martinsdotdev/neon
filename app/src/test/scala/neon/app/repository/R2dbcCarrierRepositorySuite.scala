package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.CarrierId

import java.util.UUID

class R2dbcCarrierRepositorySuite extends PostgresContainerSuite:

  private lazy val repo =
    given scala.concurrent.ExecutionContext = system.executionContext
    R2dbcCarrierRepository(connectionFactory)

  private val fedexId =
    CarrierId(UUID.fromString("019e0000-0002-7000-8000-000000000001"))
  private val dhlId =
    CarrierId(UUID.fromString("019e0000-0002-7000-8000-000000000004"))
  private val unknownId =
    CarrierId(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  describe("R2dbcCarrierRepository"):
    describe("findById"):
      it("should return an active carrier by id"):
        val result = repo.findById(fedexId).futureValue
        assert(result.isDefined)
        val carrier = result.get
        assert(carrier.id == fedexId)
        assert(carrier.code == "FDX")
        assert(carrier.name == "FedEx")
        assert(carrier.active)

      it("should return an inactive carrier by id"):
        val result = repo.findById(dhlId).futureValue
        assert(result.isDefined)
        val carrier = result.get
        assert(carrier.id == dhlId)
        assert(carrier.code == "DHL")
        assert(carrier.name == "DHL Express")
        assert(!carrier.active)

      it("should return None for an unknown id"):
        val result = repo.findById(unknownId).futureValue
        assert(result.isEmpty)
