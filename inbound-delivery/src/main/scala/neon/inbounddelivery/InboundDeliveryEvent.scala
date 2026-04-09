package neon.inbounddelivery

import neon.common.serialization.CborSerializable
import neon.common.{InboundDeliveryId, LotAttributes, PackagingLevel, SkuId}

import java.time.Instant

/** Domain events emitted by [[InboundDelivery]] state transitions. */
sealed trait InboundDeliveryEvent extends CborSerializable:
  /** The inbound delivery that emitted this event. */
  def inboundDeliveryId: InboundDeliveryId

  /** The instant at which the event occurred. */
  def occurredAt: Instant

/** Event definitions for the [[InboundDelivery]] aggregate. */
object InboundDeliveryEvent:

  /** Emitted when a new inbound delivery is created. */
  case class InboundDeliveryCreated(
      inboundDeliveryId: InboundDeliveryId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lotAttributes: LotAttributes,
      expectedQuantity: Int,
      occurredAt: Instant
  ) extends InboundDeliveryEvent

  /** Emitted when receiving begins on an inbound delivery. */
  case class ReceivingStarted(
      inboundDeliveryId: InboundDeliveryId,
      occurredAt: Instant
  ) extends InboundDeliveryEvent

  /** Emitted when a quantity is received against an inbound delivery. */
  case class QuantityReceived(
      inboundDeliveryId: InboundDeliveryId,
      quantity: Int,
      rejectedQuantity: Int,
      occurredAt: Instant
  ) extends InboundDeliveryEvent

  /** Emitted when an inbound delivery is fully received. */
  case class InboundDeliveryReceived(
      inboundDeliveryId: InboundDeliveryId,
      receivedQuantity: Int,
      occurredAt: Instant
  ) extends InboundDeliveryEvent

  /** Emitted when an inbound delivery is closed with forced rejection. */
  case class InboundDeliveryClosed(
      inboundDeliveryId: InboundDeliveryId,
      receivedQuantity: Int,
      rejectedQuantity: Int,
      occurredAt: Instant
  ) extends InboundDeliveryEvent

  /** Emitted when an inbound delivery is cancelled before receiving. */
  case class InboundDeliveryCancelled(
      inboundDeliveryId: InboundDeliveryId,
      occurredAt: Instant
  ) extends InboundDeliveryEvent
