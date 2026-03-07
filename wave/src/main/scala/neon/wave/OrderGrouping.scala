package neon.wave

/** Strategy for grouping orders within a wave.
  *
  * Determines whether orders are fulfilled individually or consolidated together during picking and
  * shipping.
  */
enum OrderGrouping:

  /** Each order is fulfilled individually without consolidation. */
  case Single

  /** Multiple orders are consolidated into groups for batch processing. */
  case Multi
