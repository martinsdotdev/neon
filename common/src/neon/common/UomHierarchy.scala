package neon.common

/** Maps coarse packaging levels to their size in eaches.
  *
  * Each is the implicit base unit and never appears as a key. Example:
  * `UomHierarchy(Pallet -> 20, Case -> 6)` means 1 Pallet = 20 eaches, 1 Case = 6 eaches.
  */
opaque type UomHierarchy = Map[PackagingLevel, Int]

object UomHierarchy:
  val empty: UomHierarchy = Map.empty

  def apply(entries: (PackagingLevel, Int)*): UomHierarchy =
    val m = Map(entries*)
    require(
      !m.contains(PackagingLevel.Each),
      "Each is the implicit base unit and must not appear in the hierarchy"
    )
    require(m.values.forall(_ > 0), "all eaches-per-unit values must be positive")
    m

  extension (h: UomHierarchy)
    def get(level: PackagingLevel): Option[Int] = h.get(level)
    def apply(level: PackagingLevel): Int = h(level)
    def contains(level: PackagingLevel): Boolean = h.contains(level)
    def nonEmpty: Boolean = h.nonEmpty
    def isEmpty: Boolean = h.isEmpty
