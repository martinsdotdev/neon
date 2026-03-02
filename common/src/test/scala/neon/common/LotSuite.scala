package neon.common

import org.scalatest.funspec.AnyFunSpec

class LotSuite extends AnyFunSpec:
  describe("Lot"):
    it("wraps a non-empty string and exposes it via .value"):
      val lot = Lot("ABC-123")
      assert(lot.value == "ABC-123")

    it("rejects empty string to prevent silent data corruption"):
      assertThrows[IllegalArgumentException]:
        Lot("")
