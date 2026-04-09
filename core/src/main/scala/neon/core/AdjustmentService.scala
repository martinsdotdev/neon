package neon.core

import neon.common.{AdjustmentReasonCode, UserId}

import java.time.Instant

/** Errors that can occur during inventory adjustment from count variances. */
sealed trait AdjustmentError

object AdjustmentError:
  /** SOX Section 404 segregation of duties violation: the person adjusting inventory must not be
    * the same person who counted it.
    */
  case class SegregationOfDutiesViolation(countedBy: UserId, adjustedBy: UserId)
      extends AdjustmentError

/** The result of a successful adjustment from a count variance.
  *
  * @param variance
  *   the count variance that was adjusted
  * @param adjustedBy
  *   the user who approved and applied the adjustment
  * @param reasonCode
  *   the SOX-compliant reason code for the adjustment
  */
case class AdjustmentResult(
    variance: CountVariance,
    adjustedBy: UserId,
    reasonCode: AdjustmentReasonCode
)

/** Applies inventory adjustments from approved cycle count variances with SOX Section 404
  * compliance. Validates segregation of duties: the user adjusting inventory must differ from the
  * user who performed the count.
  */
object AdjustmentService:

  /** Validates and prepares an adjustment for a count variance.
    *
    * @param variance
    *   the count variance to adjust
    * @param adjustedBy
    *   the user applying the adjustment
    * @param reasonCode
    *   the reason code for the adjustment
    * @param at
    *   instant of the adjustment
    * @return
    *   adjustment result or segregation of duties error
    */
  def adjust(
      variance: CountVariance,
      adjustedBy: UserId,
      reasonCode: AdjustmentReasonCode,
      at: Instant
  ): Either[AdjustmentError, AdjustmentResult] =
    if adjustedBy == variance.countedBy then
      Left(AdjustmentError.SegregationOfDutiesViolation(variance.countedBy, adjustedBy))
    else Right(AdjustmentResult(variance, adjustedBy, reasonCode))
