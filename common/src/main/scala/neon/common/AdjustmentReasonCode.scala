package neon.common

/** Reason code for inventory adjustments. Required for SOX Section 404 compliance. Categories
  * derived from Oracle WMS, SAP EWM, and Dynamics 365 standard reason code sets.
  */
enum AdjustmentReasonCode:

  // Shrinkage
  /** Physically damaged in warehouse. */
  case Damaged

  /** Past shelf life expiration date. */
  case Expired

  /** Unaccounted inventory loss. */
  case Shrinkage

  // Operational
  /** Adjustment from cycle count variance. */
  case CycleCountAdjustment

  /** Difference between ASN and actual received quantity. */
  case ReceivingDiscrepancy

  /** Inventory found in wrong location. */
  case Misplaced

  /** Previously missing inventory located. */
  case Found

  // Quality
  /** Moved to quality inspection hold. */
  case QualityHold

  /** Released from quality hold back to available. */
  case QualityRelease

  /** Manufacturing defect identified. */
  case Defective

  // Business
  /** Consumed for internal operations (samples, testing). */
  case InternalUse

  /** Disposed of or destroyed. */
  case Disposal

  // Data correction
  /** Correcting a previous incorrect entry. */
  case DataCorrection
