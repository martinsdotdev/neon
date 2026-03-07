package neon.common

/** Priority level for orders and tasks, from lowest to highest urgency. */
enum Priority:

  /** Low priority — processed after higher-priority work. */
  case Low

  /** Normal priority — default for standard orders. */
  case Normal

  /** High priority — expedited processing. */
  case High

  /** Critical priority — immediate processing required. */
  case Critical
