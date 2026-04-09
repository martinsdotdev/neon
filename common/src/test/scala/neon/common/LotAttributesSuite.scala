package neon.common

import org.scalatest.funspec.AnyFunSpec

import java.time.LocalDate

class LotAttributesSuite extends AnyFunSpec:

  describe("LotAttributes"):

    describe("creating"):

      it("accepts all-None fields for untracked SKUs"):
        val attrs = LotAttributes()
        assert(attrs.lot.isEmpty)
        assert(attrs.expirationDate.isEmpty)
        assert(attrs.productionDate.isEmpty)
        assert(attrs.serialNumber.isEmpty)

      it("accepts lot with expiration date for FEFO-managed SKUs"):
        val attrs = LotAttributes(
          lot = Some(Lot("LOT-001")),
          expirationDate = Some(LocalDate.of(2027, 6, 30))
        )
        assert(attrs.lot.contains(Lot("LOT-001")))
        assert(attrs.expirationDate.contains(LocalDate.of(2027, 6, 30)))

      it("accepts full GS1 AI set (lot, expiry, production date, serial number)"):
        val attrs = LotAttributes(
          lot = Some(Lot("BATCH-2026Q1")),
          expirationDate = Some(LocalDate.of(2027, 3, 31)),
          productionDate = Some(LocalDate.of(2026, 1, 15)),
          serialNumber = Some("SN-000042")
        )
        assert(attrs.lot.contains(Lot("BATCH-2026Q1")))
        assert(attrs.expirationDate.contains(LocalDate.of(2027, 3, 31)))
        assert(attrs.productionDate.contains(LocalDate.of(2026, 1, 15)))
        assert(attrs.serialNumber.contains("SN-000042"))

    describe("remaining shelf life"):

      it("computes remaining days from expiration date and reference date"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2027, 1, 10)))
        assert(attrs.remainingShelfLifeDays(LocalDate.of(2027, 1, 1)) == 9)

      it("returns zero when expiration date equals reference date"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2027, 1, 1)))
        assert(attrs.remainingShelfLifeDays(LocalDate.of(2027, 1, 1)) == 0)

      it("returns zero for expired stock"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2026, 12, 31)))
        assert(attrs.remainingShelfLifeDays(LocalDate.of(2027, 1, 5)) == 0)

      it("returns Int.MaxValue when no expiration date is set"):
        val attrs = LotAttributes()
        assert(attrs.remainingShelfLifeDays(LocalDate.of(2027, 1, 1)) == Int.MaxValue)

    describe("expiration check"):

      it("reports not expired when expiration date is in the future"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2027, 6, 30)))
        assert(!attrs.isExpired(LocalDate.of(2027, 1, 1)))

      it("reports expired when expiration date is in the past"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2026, 12, 31)))
        assert(attrs.isExpired(LocalDate.of(2027, 1, 1)))

      it("reports expired when expiration date equals reference date"):
        val attrs = LotAttributes(expirationDate = Some(LocalDate.of(2027, 1, 1)))
        assert(attrs.isExpired(LocalDate.of(2027, 1, 1)))

      it("reports not expired when no expiration date is set"):
        val attrs = LotAttributes()
        assert(!attrs.isExpired(LocalDate.of(2027, 1, 1)))
