package neon.app.auth

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Factory.Argon2Types

class PasswordHasher:

  private val argon2 = Argon2Factory.create(Argon2Types.ARGON2id)
  private val iterations = 2
  private val memoryKiB = 19456
  private val parallelism = 1

  def hash(password: String): String =
    argon2.hash(iterations, memoryKiB, parallelism, password.toCharArray)

  def verify(password: String, hash: String): Boolean =
    argon2.verify(hash, password.toCharArray)
