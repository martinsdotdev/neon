package neon.common

/** Maps coarse packaging levels to their size in eaches.
  *
  * Each is the implicit base unit and never appears as a key. Example:
  * `UomHierarchy(Pallet -> 20, Case -> 6)` means 1 Pallet = 20 eaches, 1 Case = 6 eaches.
  */
opaque type UomHierarchy = Map[PackagingLevel, Int]

object UomHierarchy:

  /** An empty hierarchy with no packaging level mappings. */
  val empty: UomHierarchy = Map.empty

  /** Creates a hierarchy from packaging level to eaches-per-unit mappings.
    *
    * @param entries
    *   pairs of (level, eaches-per-unit); `Each` must not appear
    * @throws IllegalArgumentException
    *   if `Each` is included or any value is non-positive
    */
  def apply(entries: (PackagingLevel, Int)*): UomHierarchy =
    val m = Map(entries*)
    require(
      !m.contains(PackagingLevel.Each),
      "Each is the implicit base unit and must not appear in the hierarchy"
    )
    require(m.values.forall(_ > 0), "all eaches-per-unit values must be positive")
    m

  extension (h: UomHierarchy)
    /** Returns the eaches-per-unit for the given level, if defined. */
    def get(level: PackagingLevel): Option[Int] = h.get(level)

    /** Returns the eaches-per-unit for the given level.
      *
      * @throws NoSuchElementException
      *   if the level is not in the hierarchy
      */
    def apply(level: PackagingLevel): Int = h(level)

    /** Tests whether this hierarchy defines a mapping for the given level. */
    def contains(level: PackagingLevel): Boolean = h.contains(level)

    /** Tests whether this hierarchy has at least one level mapping. */
    def nonEmpty: Boolean = h.nonEmpty

    /** Tests whether this hierarchy has no level mappings. */
    def isEmpty: Boolean = h.isEmpty
