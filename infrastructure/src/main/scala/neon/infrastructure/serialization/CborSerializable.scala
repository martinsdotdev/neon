package neon.infrastructure.serialization

/** Marker trait for types serialized via Jackson CBOR in the Pekko
  * journal and cluster. All actor commands, responses, state wrappers,
  * and event envelopes must mix in this trait.
  *
  * The binding to `JacksonCborSerializer` is declared in `serialization.conf`.
  */
trait CborSerializable
