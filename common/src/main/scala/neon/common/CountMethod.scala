package neon.common

/** Cycle count method determining what information the counter receives. Blind counting is the
  * industry best practice per APICS/ASCM as it eliminates confirmation bias.
  */
enum CountMethod:

  /** Counter does not see expected quantity. Eliminates confirmation bias. */
  case Blind

  /** Counter sees expected quantity. Faster but less accurate. */
  case Informed
