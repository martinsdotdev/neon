package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.{LocationId, ZoneId}
import neon.location.LocationType

import java.util.UUID

class R2dbcLocationRepositorySuite extends PostgresContainerSuite:

  private lazy val repo =
    given scala.concurrent.ExecutionContext = system.executionContext
    R2dbcLocationRepository(connectionFactory)

  private val pickA01Id =
    LocationId(UUID.fromString("019e0000-0003-7000-8000-000000000001"))
  private val zoneAId =
    ZoneId(UUID.fromString("019e0000-00ff-7000-8000-00000000000a"))
  private val dock01Id =
    LocationId(UUID.fromString("019e0000-0003-7000-8000-000000000020"))
  private val reserveA01Id =
    LocationId(UUID.fromString("019e0000-0003-7000-8000-000000000006"))
  private val unknownId =
    LocationId(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  describe("R2dbcLocationRepository"):
    describe("findById"):
      it("should return a pick location with zone and picking sequence"):
        val result = repo.findById(pickA01Id).futureValue
        assert(result.isDefined)
        val location = result.get
        assert(location.id == pickA01Id)
        assert(location.code == "PICK-A01")
        assert(location.locationType == LocationType.Pick)
        assert(location.zoneId.contains(zoneAId))
        assert(location.pickingSequence.contains(1))

      it("should return a dock location with no zone"):
        val result = repo.findById(dock01Id).futureValue
        assert(result.isDefined)
        val location = result.get
        assert(location.id == dock01Id)
        assert(location.code == "DOCK-01")
        assert(location.locationType == LocationType.Dock)
        assert(location.zoneId.isEmpty)
        assert(location.pickingSequence.isEmpty)

      it("should return a reserve location with null picking sequence"):
        val result = repo.findById(reserveA01Id).futureValue
        assert(result.isDefined)
        val location = result.get
        assert(location.code == "RSV-A01")
        assert(location.locationType == LocationType.Reserve)
        assert(location.pickingSequence.isEmpty)

      it("should return None for an unknown id"):
        val result = repo.findById(unknownId).futureValue
        assert(result.isEmpty)
