package neon.app

enum DispatchCriterion:
  case WaveSequence // oldest wave first — UUID v7 time-ordered comparison
  case OrderPriority // highest priority first — Critical > High > Normal > Low
  case OrderSimplicity // single-line orders before multi-line
  case GroupCompletion // highest completion % first — reduces WIP
  case PackagingTier // larger packaging unit first — Pallet > Case > Each

case class DispatchProfile(criteria: List[DispatchCriterion])
