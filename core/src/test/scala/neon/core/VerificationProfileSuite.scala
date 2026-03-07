package neon.core

import neon.common.PackagingLevel
import org.scalatest.funspec.AnyFunSpec

class VerificationProfileSuite extends AnyFunSpec:
  describe("VerificationProfile"):
    describe("requiresVerification"):
      it("returns true for a packaging level present in the set"):
        val profile = VerificationProfile(Set(PackagingLevel.Each))
        assert(profile.requiresVerification(PackagingLevel.Each))

      it("returns false for a packaging level absent from the set"):
        val profile = VerificationProfile(Set(PackagingLevel.Each))
        assert(!profile.requiresVerification(PackagingLevel.Case))

      it("supports multiple packaging levels"):
        val profile = VerificationProfile(Set(PackagingLevel.Each, PackagingLevel.Case))
        assert(profile.requiresVerification(PackagingLevel.Each))
        assert(profile.requiresVerification(PackagingLevel.Case))
        assert(!profile.requiresVerification(PackagingLevel.Pallet))

    describe("disabled"):
      it("returns false for any packaging level"):
        val profile = VerificationProfile.disabled
        PackagingLevel.values.foreach { level =>
          assert(!profile.requiresVerification(level))
        }
