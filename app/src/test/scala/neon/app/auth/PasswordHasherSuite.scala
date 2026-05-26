package neon.app.auth

import org.scalatest.funspec.AnyFunSpec

class PasswordHasherSuite extends AnyFunSpec:

  private val hasher = PasswordHasher()

  describe("PasswordHasher"):
    it("verifies a correct password against its hash"):
      val hash = hasher.hash("warehouse-secret")
      assert(hasher.verify(password = "warehouse-secret", hash = hash))

    it("rejects an incorrect password"):
      val hash = hasher.hash("correct-password")
      assert(!hasher.verify(password = "wrong-password", hash = hash))
