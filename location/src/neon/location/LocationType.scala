package neon.location

/** The functional type of a warehouse [[Location]], determining its role in the fulfillment flow.
  */
enum LocationType:

  /** Picking face — forward location where operators pick items. */
  case Pick

  /** Reserve/overstock storage for replenishment to picking faces. */
  case Reserve

  /** Intermediate buffer area for consolidation before workstation. */
  case Buffer

  /** Staging area for outbound shipment preparation. */
  case Staging

  /** Packing station area for pack operations. */
  case Packing

  /** Dock location for loading and shipping. */
  case Dock
