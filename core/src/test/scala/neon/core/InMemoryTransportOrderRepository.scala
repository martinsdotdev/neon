package neon.core

import neon.common.{HandlingUnitId, TransportOrderId}
import neon.transportorder.{TransportOrder, TransportOrderEvent, TransportOrderRepository}

import scala.collection.mutable

class InMemoryTransportOrderRepository extends TransportOrderRepository:
  val store: mutable.Map[TransportOrderId, TransportOrder] =
    mutable.Map.empty
  val events: mutable.ListBuffer[TransportOrderEvent] =
    mutable.ListBuffer.empty
  def findById(id: TransportOrderId): Option[TransportOrder] =
    store.get(id)
  def findByHandlingUnitId(
      handlingUnitId: HandlingUnitId
  ): List[TransportOrder] =
    store.values.filter(_.handlingUnitId == handlingUnitId).toList
  def save(
      transportOrder: TransportOrder,
      event: TransportOrderEvent
  ): Unit =
    store(transportOrder.id) = transportOrder
    events += event
  def saveAll(
      entries: List[(TransportOrder, TransportOrderEvent)]
  ): Unit =
    entries.foreach((transportOrder, event) => save(transportOrder, event))
