package neon.inbounddelivery

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{InboundDeliveryId, LotAttributes, PackagingLevel, SkuId}

import java.time.Instant

/** Typestate-encoded aggregate for inbound delivery lifecycle management.
  *
  * An inbound delivery represents an expected receipt of goods into the warehouse. The state
  * machine follows: [[InboundDelivery.New]] -> [[InboundDelivery.Receiving]] ->
  * [[InboundDelivery.Received]] | [[InboundDelivery.Closed]], with [[InboundDelivery.Cancelled]]
  * reachable only from [[InboundDelivery.New]]. Transitions are only available on valid source
  * states, enforced at compile time.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait InboundDelivery:
  /** The unique identifier of this inbound delivery. */
  def id: InboundDeliveryId

  /** The SKU being received. */
  def skuId: SkuId

  /** The packaging level of the received goods. */
  def packagingLevel: PackagingLevel

  /** The lot tracking attributes for the received goods. */
  def lotAttributes: LotAttributes

  /** The total expected quantity for this delivery. */
  def expectedQuantity: Int

/** Factory and state definitions for the [[InboundDelivery]] aggregate. */
object InboundDelivery:

  /** An inbound delivery that has been announced but not yet started receiving.
    *
    * Transitions: [[startReceiving]] -> [[Receiving]], [[cancel]] -> [[Cancelled]].
    */
  case class New(
      id: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int
  ) extends InboundDelivery:
    require(expectedQuantity > 0, s"expectedQuantity must be positive, got $expectedQuantity")

    /** Begins the receiving process, transitioning to [[Receiving]].
      *
      * @param at
      *   instant of the transition
      * @return
      *   receiving state and event
      */
    def startReceiving(
        at: Instant
    ): (Receiving, InboundDeliveryEvent.ReceivingStarted) =
      val receiving =
        Receiving(id, skuId, packagingLevel, lotAttributes, expectedQuantity, 0, 0)
      val event = InboundDeliveryEvent.ReceivingStarted(id, at)
      (receiving, event)

    /** Cancels this delivery before receiving begins.
      *
      * @param at
      *   instant of the cancellation
      * @return
      *   cancelled state and event
      */
    def cancel(at: Instant): (Cancelled, InboundDeliveryEvent.InboundDeliveryCancelled) =
      val cancelled =
        Cancelled(id, skuId, packagingLevel, lotAttributes, expectedQuantity)
      val event = InboundDeliveryEvent.InboundDeliveryCancelled(id, at)
      (cancelled, event)

  /** An inbound delivery actively being received.
    *
    * Invariant: `expectedQuantity >= receivedQuantity + rejectedQuantity`.
    *
    * Transitions: [[receive]] -> [[Receiving]], [[complete]] -> [[Received]] (when fully received),
    * [[close]] -> [[Closed]] (forces remaining as rejected).
    */
  case class Receiving(
      id: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int,
      receivedQuantity: Int,
      rejectedQuantity: Int
  ) extends InboundDelivery:

    /** Records a quantity received and optionally rejected.
      *
      * @param quantity
      *   the quantity accepted
      * @param rejected
      *   the quantity rejected in this batch
      * @param at
      *   instant of the receipt
      * @return
      *   updated receiving state and event
      */
    def receive(
        quantity: Int,
        rejected: Int,
        at: Instant
    ): (Receiving, InboundDeliveryEvent.QuantityReceived) =
      require(
        quantity > 0 || rejected > 0,
        "at least one of quantity or rejected must be positive"
      )
      require(quantity >= 0, s"quantity must be non-negative, got $quantity")
      require(rejected >= 0, s"rejected must be non-negative, got $rejected")
      val newReceived = receivedQuantity + quantity
      val newRejected = rejectedQuantity + rejected
      require(
        expectedQuantity >= newReceived + newRejected,
        s"received $newReceived + rejected $newRejected exceeds expected $expectedQuantity"
      )
      val updated = copy(receivedQuantity = newReceived, rejectedQuantity = newRejected)
      val event = InboundDeliveryEvent.QuantityReceived(id, quantity, rejected, at)
      (updated, event)

    /** Returns true when all expected quantity has been accounted for. */
    def isFullyReceived: Boolean =
      expectedQuantity == receivedQuantity + rejectedQuantity

    /** Completes the delivery when fully received.
      *
      * @param at
      *   instant of the completion
      * @return
      *   received state and event
      */
    def complete(at: Instant): (Received, InboundDeliveryEvent.InboundDeliveryReceived) =
      require(isFullyReceived, "cannot complete delivery that is not fully received")
      val received = Received(
        id,
        skuId,
        packagingLevel,
        lotAttributes,
        expectedQuantity,
        receivedQuantity,
        rejectedQuantity
      )
      val event = InboundDeliveryEvent.InboundDeliveryReceived(id, receivedQuantity, at)
      (received, event)

    /** Closes the delivery, forcing any remaining quantity as rejected.
      *
      * @param at
      *   instant of the close
      * @return
      *   closed state and event
      */
    def close(at: Instant): (Closed, InboundDeliveryEvent.InboundDeliveryClosed) =
      val forcedRejected = expectedQuantity - receivedQuantity
      val closed = Closed(
        id,
        skuId,
        packagingLevel,
        lotAttributes,
        expectedQuantity,
        receivedQuantity,
        forcedRejected
      )
      val event =
        InboundDeliveryEvent.InboundDeliveryClosed(id, receivedQuantity, forcedRejected, at)
      (closed, event)

  /** An inbound delivery whose expected quantity has been fully accounted for. Terminal state. */
  case class Received(
      id: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int,
      receivedQuantity: Int,
      rejectedQuantity: Int
  ) extends InboundDelivery

  /** An inbound delivery that was closed with forced rejection of remaining quantity. Terminal
    * state.
    */
  case class Closed(
      id: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int,
      receivedQuantity: Int,
      rejectedQuantity: Int
  ) extends InboundDelivery

  /** An inbound delivery cancelled before receiving began. Terminal state. */
  case class Cancelled(
      id: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int
  ) extends InboundDelivery
