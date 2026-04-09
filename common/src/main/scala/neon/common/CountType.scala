package neon.common

/** Cycle count type per SAP EWM and Oracle WMS standard categories. */
enum CountType:

  /** Standard scheduled count (SAP: Periodic). */
  case Planned

  /** Random sample count (SAP: Ad Hoc). */
  case Random

  /** Movement-triggered opportunistic count (SAP: Continuous). */
  case Triggered

  /** Recount of prior variance (SAP EWM standard term). */
  case Recount
