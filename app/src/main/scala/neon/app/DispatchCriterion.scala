package neon.app

/** Criterion for ordering tasks during dispatch.
  *
  * Used by [[TaskDispatchPolicy]] to compose multi-level sort keys via a [[DispatchProfile]].
  */
enum DispatchCriterion:

  /** Orders tasks by wave id (UUID v7 time-ordered), earliest first. Tasks without a wave sort
    * last.
    */
  case WaveSequence

  /** Orders tasks by their order's priority level, highest first. */
  case OrderPriority

  /** Orders single-line orders before multi-line orders. */
  case OrderSimplicity

  /** Orders tasks whose consolidation group has the highest completion ratio first, favoring
    * near-complete groups.
    */
  case GroupCompletion

  /** Orders tasks by packaging level ordinal, lowest tier first. */
  case PackagingTier

/** Configurable dispatch ordering composed of ranked criteria.
  *
  * Criteria are evaluated in list order; earlier criteria take precedence over later ones as
  * tie-breakers.
  *
  * @param criteria
  *   the ordered list of dispatch criteria to apply
  */
case class DispatchProfile(criteria: List[DispatchCriterion])
