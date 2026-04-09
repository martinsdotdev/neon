package neon.common

/** Stock allocation strategy, configurable per SKU or SKU category. Determines the sort order when
  * selecting inventory positions to fulfill outbound demand. Based on SAP EWM composable sort key
  * pattern.
  */
enum AllocationStrategy:

  /** First Expired First Out. Mandatory for pharma (FDA 21 CFR 211) and food (EU GDP, FSMA). Sort:
    * expiration date ASC, then production date ASC, then available quantity ASC.
    */
  case Fefo

  /** First In First Out. GAAP preferred (ASC 330). Sort: production date ASC, then available
    * quantity ASC.
    */
  case Fifo

  /** Minimize pick travel distance by selecting nearest locations first. */
  case NearestLocation
