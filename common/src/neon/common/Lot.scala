package neon.common

opaque type Lot = String

object Lot:
  def apply(value: String): Lot =
    require(value.nonEmpty, "lot must not be empty")
    value
  extension (l: Lot) def value: String = l
