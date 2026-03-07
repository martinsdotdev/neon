package neon.transportorder

import neon.common.{HandlingUnitId, TransportOrderId}

/** Port trait for [[TransportOrder]] aggregate persistence and queries. */
trait TransportOrderRepository:
  /** Finds a transport order by its unique identifier.
    *
    * @param id
    *   the transport order identifier
    * @return
    *   the transport order if it exists, [[None]] otherwise
    */
  def findById(id: TransportOrderId): Option[TransportOrder]

  /** Finds all transport orders associated with a handling unit.
    *
    * @param handlingUnitId
    *   the handling unit identifier
    * @return
    *   all transport orders for the given handling unit
    */
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[TransportOrder]

  /** Persists a transport order along with the event that caused the state change.
    *
    * @param transportOrder
    *   the transport order to persist
    * @param event
    *   the domain event to store
    */
  def save(transportOrder: TransportOrder, event: TransportOrderEvent): Unit

  /** Persists multiple transport orders with their associated events atomically.
    *
    * @param entries
    *   pairs of transport order and corresponding event
    */
  def saveAll(entries: List[(TransportOrder, TransportOrderEvent)]): Unit
