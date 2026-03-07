package neon.workstation

/** The physical type of a workstation, determining its operational capabilities. */
enum WorkstationType:
  /** Put-wall station for order deconsolidation. */
  case PutWall

  /** Packing station for outbound preparation. */
  case PackStation
