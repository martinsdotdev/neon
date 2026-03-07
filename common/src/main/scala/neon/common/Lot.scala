package neon.common

/** Lot identifier for inventory tracking (GS1 AI-10 "Batch or Lot Number").
  *
  * A non-empty string wrapping the lot code. SKUs without lot tracking use `Option[Lot] = None` on
  * the [[neon.inventory.Inventory]] triad.
  */
opaque type Lot = String

object Lot:
  /** Creates a Lot from a non-empty string.
    *
    * @param value
    *   the lot code
    * @throws IllegalArgumentException
    *   if `value` is empty
    */
  def apply(value: String): Lot =
    require(value.nonEmpty, "lot must not be empty")
    value

  /** Returns the underlying string value. */
  extension (l: Lot) def value: String = l
