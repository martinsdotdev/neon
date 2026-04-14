error id: file://<WORKSPACE>/app/src/main/scala/neon/app/auth/SessionToken.scala:scala/Byte#
file://<WORKSPACE>/app/src/main/scala/neon/app/auth/SessionToken.scala
empty definition using pc, found symbol in pc: scala/Byte#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -Byte#
	 -scala/Predef.Byte#
offset: 250
uri: file://<WORKSPACE>/app/src/main/scala/neon/app/auth/SessionToken.scala
text:
```scala
package neon.app.auth

import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

object SessionToken:

  private val random = new SecureRandom()
  private val tokenBytes = 20

  def generate(): String =
    val bytes = new Array[Byt@@e](tokenBytes)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

  def hash(token: String): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(token.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/Byte#