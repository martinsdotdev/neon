package neon.transportorder

import neon.common.{HandlingUnitId, TransportOrderId}

/** Port trait for TransportOrder aggregate persistence and queries. */
trait TransportOrderRepository:
  def findById(id: TransportOrderId): Option[TransportOrder]
  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): List[TransportOrder]
  def save(transportOrder: TransportOrder, event: TransportOrderEvent): Unit
  def saveAll(entries: List[(TransportOrder, TransportOrderEvent)]): Unit
