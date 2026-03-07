package neon.common

/** GS1 packaging hierarchy level, from coarsest to finest.
  *
  * Used across [[neon.handlingunit.HandlingUnit]], [[neon.inventory.Inventory]], and
  * [[neon.order.OrderLine]] to describe the physical packaging of goods.
  */
enum PackagingLevel:

  /** Pallet-level packaging (outermost transport unit). */
  case Pallet

  /** Case-level packaging (intermediate shipping unit). */
  case Case

  /** Inner pack within a case. */
  case InnerPack

  /** Individual sellable unit (finest granularity). */
  case Each
