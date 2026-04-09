package neon.common

/** Stock disposition status per ISA-95 material model and SAP EWM stock types. Determines whether
  * stock is eligible for allocation.
  */
enum InventoryStatus:

  /** Unrestricted stock, eligible for allocation. */
  case Available

  /** Pending quality inspection (ISO 2859-1). Not allocatable. */
  case QualityHold

  /** Physically damaged stock. Not allocatable. */
  case Damaged

  /** Administrative hold: recall, regulatory block. Not allocatable. */
  case Blocked

  /** Past shelf life per GS1 AI-17 expiration date (FDA 21 CFR 211.184). Not allocatable. */
  case Expired
