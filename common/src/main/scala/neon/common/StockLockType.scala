package neon.common

/** Reason for locking stock, determining which quantity bucket receives the locked amount.
  *
  * [[Outbound]] routes to `allocatedQuantity`; all other types route to `reservedQuantity`.
  */
enum StockLockType:

  /** Picking or shipping allocation. Routes to allocated bucket. */
  case Outbound

  /** Receiving or putaway hold. Routes to reserved bucket. */
  case Inbound

  /** Relocation within the warehouse. Routes to reserved bucket. */
  case InternalMove

  /** Cycle counting hold. Routes to reserved bucket. */
  case Count

  /** Inventory correction hold. Routes to reserved bucket. */
  case Adjustment
