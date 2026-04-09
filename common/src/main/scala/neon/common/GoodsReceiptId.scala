package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a goods receipt (physical receiving session). Backed by a time-ordered
  * UUID v7.
  */
opaque type GoodsReceiptId = UUID

object GoodsReceiptId:
  /** Generates a new GoodsReceiptId backed by a time-ordered UUID v7. */
  def apply(): GoodsReceiptId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a GoodsReceiptId. */
  def apply(value: UUID): GoodsReceiptId = value

  /** Returns the underlying UUID value. */
  extension (id: GoodsReceiptId) def value: UUID = id
