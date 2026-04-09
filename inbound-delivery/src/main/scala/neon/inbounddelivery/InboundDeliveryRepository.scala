package neon.inbounddelivery

import neon.common.InboundDeliveryId

/** Port trait for [[InboundDelivery]] aggregate persistence and queries. */
trait InboundDeliveryRepository:

  /** Finds an inbound delivery by its unique identifier.
    *
    * @param id
    *   the inbound delivery identifier
    * @return
    *   the inbound delivery if it exists, [[None]] otherwise
    */
  def findById(id: InboundDeliveryId): Option[InboundDelivery]

  /** Persists an inbound delivery state together with the event that caused the transition.
    *
    * @param delivery
    *   the inbound delivery state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(delivery: InboundDelivery, event: InboundDeliveryEvent): Unit
