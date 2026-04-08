package neon.app.auth

import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

object SessionToken:

  private val random = new SecureRandom()
  private val tokenBytes = 20

  def generate(): String =
    val bytes = new Array[Byte](tokenBytes)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

  def hash(token: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(token.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString
