package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.sku.Sku]]. Backed by a time-ordered UUID v7. */
opaque type SkuId = UUID

object SkuId:
  /** Generates a new SkuId backed by a time-ordered UUID v7. */
  def apply(): SkuId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a SkuId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): SkuId = value

  /** Returns the underlying UUID value. */
  extension (id: SkuId) def value: UUID = id
