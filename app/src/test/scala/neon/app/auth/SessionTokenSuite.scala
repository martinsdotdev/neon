package neon.app.auth

import org.scalatest.funspec.AnyFunSpec

class SessionTokenSuite extends AnyFunSpec:

  describe("SessionToken"):
    it("generates tokens with at least 160 bits of entropy"):
      val token = SessionToken.generate()
      val decoded =
        java.util.Base64.getUrlDecoder.decode(token)
      assert(decoded.length >= 20)

    it("produces a deterministic 64-char SHA-256 hex hash"):
      val hash1 = SessionToken.hash("test-token")
      val hash2 = SessionToken.hash("test-token")
      assert(hash1 == hash2)
      assert(hash1.length == 64)

    it("produces different hashes for different tokens"):
      val hash1 = SessionToken.hash("token-a")
      val hash2 = SessionToken.hash("token-b")
      assert(hash1 != hash2)
