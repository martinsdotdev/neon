package neon.location

import neon.common.{LocationId, ZoneId}
import org.scalatest.funspec.AnyFunSpec

class LocationSuite extends AnyFunSpec:
  val id = LocationId()
  val zoneId = ZoneId()

  describe("Location"):
    describe("construction"):
      it("carries reference data and zone assignment"):
        val loc = Location(id, "A-01-01", Some(zoneId), LocationType.Pick)
        assert(loc.id == id)
        assert(loc.code == "A-01-01")
        assert(loc.zoneId.contains(zoneId))
        assert(loc.locationType == LocationType.Pick)

      it("defaults pickingSequence to None"):
        val loc = Location(id, "A-01-01", Some(zoneId), LocationType.Pick)
        assert(loc.pickingSequence.isEmpty)

    describe("picking sequence"):
      it("carries route-optimized ordering index when assigned"):
        val loc =
          Location(id, "A-01-01", Some(zoneId), LocationType.Pick, pickingSequence = Some(42))
        assert(loc.pickingSequence.contains(42))
