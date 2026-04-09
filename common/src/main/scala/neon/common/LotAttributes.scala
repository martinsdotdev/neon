package neon.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** GS1 Application Identifier-aligned lot/batch tracking attributes.
  *
  * @param lot
  *   GS1 AI-10: batch/lot number
  * @param expirationDate
  *   GS1 AI-17: shelf life expiration date (mandatory for FEFO allocation)
  * @param productionDate
  *   GS1 AI-11: date of manufacture
  * @param serialNumber
  *   GS1 AI-21: item-level serialization
  */
case class LotAttributes(
    lot: Option[Lot] = None,
    expirationDate: Option[LocalDate] = None,
    productionDate: Option[LocalDate] = None,
    serialNumber: Option[String] = None
):

  /** Computes the remaining shelf life in days from a reference date. Returns [[Int.MaxValue]] when
    * no expiration date is set (non-perishable). Returns zero for expired stock.
    */
  def remainingShelfLifeDays(referenceDate: LocalDate): Int =
    expirationDate match
      case None       => Int.MaxValue
      case Some(date) =>
        val days = ChronoUnit.DAYS.between(referenceDate, date).toInt
        if days < 0 then 0 else days

  /** Returns true if the stock is expired as of the reference date. Stock with no expiration date
    * is never considered expired.
    */
  def isExpired(referenceDate: LocalDate): Boolean =
    expirationDate.exists(date => !referenceDate.isBefore(date))
