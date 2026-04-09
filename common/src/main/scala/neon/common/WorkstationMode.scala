package neon.common

/** Operational mode of a workstation, determining what type of work it processes. Only one mode at
  * a time per workstation; switching is only allowed from the Idle state.
  */
enum WorkstationMode:

  /** Inbound goods receipt processing. */
  case Receiving

  /** Outbound order picking. */
  case Picking

  /** Cycle count and physical inventory operations. */
  case Counting

  /** Internal stock movement between locations. */
  case Relocation
