package neon.app

enum DispatchCriterion:
  case WaveSequence
  case OrderPriority
  case OrderSimplicity
  case GroupCompletion
  case PackagingTier

case class DispatchProfile(criteria: List[DispatchCriterion])
