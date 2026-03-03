package neon.common

import org.scalatest.funspec.AnyFunSpec

class UomHierarchySuite extends AnyFunSpec:
  describe("UomHierarchy"):
    it("creates a hierarchy from packaging level to eaches-per-unit"):
      val h = UomHierarchy(PackagingLevel.Pallet -> 20, PackagingLevel.Case -> 6)
      assert(h.get(PackagingLevel.Pallet) == Some(20))
      assert(h.get(PackagingLevel.Case) == Some(6))
      assert(h.get(PackagingLevel.InnerPack) == None)

    it("supports direct access and contains check"):
      val h = UomHierarchy(PackagingLevel.Case -> 6)
      assert(h(PackagingLevel.Case) == 6)
      assert(h.contains(PackagingLevel.Case))
      assert(!h.contains(PackagingLevel.Pallet))

    it("reports emptiness correctly"):
      assert(UomHierarchy.empty.isEmpty)
      assert(!UomHierarchy.empty.nonEmpty)
      val h = UomHierarchy(PackagingLevel.Case -> 6)
      assert(h.nonEmpty)
      assert(!h.isEmpty)

    it("rejects Each as a key since it is the implicit base unit"):
      assertThrows[IllegalArgumentException]:
        UomHierarchy(PackagingLevel.Each -> 1)

    it("rejects zero or negative eaches-per-unit values"):
      assertThrows[IllegalArgumentException]:
        UomHierarchy(PackagingLevel.Case -> 0)
      assertThrows[IllegalArgumentException]:
        UomHierarchy(PackagingLevel.Pallet -> -5)
