package neon.task

/** The type of warehouse task, determining the physical operation performed.
  *
  * Each variant maps to a canonical warehouse operation: picking items for orders, putaway of
  * received goods, replenishment of pick faces from reserve, or location-to-location transfer of
  * inventory.
  */
enum TaskType:

  /** Pick items from a location for order fulfillment. */
  case Pick

  /** Put away received goods into a storage location. */
  case Putaway

  /** Replenish a pick face from reserve storage. */
  case Replenish

  /** Transfer inventory between locations. */
  case Transfer
