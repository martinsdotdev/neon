package neon.common

import org.scalatest.funspec.AnyFunSpec

class PermissionSuite extends AnyFunSpec:
  describe("Permission"):
    it("round-trips every permission through fromKey"):
      Permission.values.foreach { permission =>
        assert(
          Permission.fromKey(permission.key).contains(permission)
        )
      }

    it("returns None for unknown key"):
      assert(Permission.fromKey("unknown:key").isEmpty)
