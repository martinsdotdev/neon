package neon.core

import neon.common.{HandlingUnitId, TransportOrderId}
import neon.transportorder.{AsyncTransportOrderRepository, TransportOrder, TransportOrderEvent}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryAsyncTransportOrderRepository(recorder: CallRecorder = CallRecorder())
    extends AsyncTransportOrderRepository:
  val store: mutable.Map[TransportOrderId, TransportOrder] = mutable.Map.empty
  val events: mutable.ListBuffer[TransportOrderEvent] = mutable.ListBuffer.empty

  def findById(id: TransportOrderId): Future[Option[TransportOrder]] =
    recorder.record("transportOrder.findById")
    Future.successful(store.get(id))

  def findByHandlingUnitId(handlingUnitId: HandlingUnitId): Future[List[TransportOrder]] =
    recorder.record("transportOrder.findByHandlingUnitId")
    Future.successful(store.values.filter(_.handlingUnitId == handlingUnitId).toList)

  def save(transportOrder: TransportOrder, event: TransportOrderEvent): Future[Unit] =
    recorder.record("transportOrder.save")
    store(transportOrder.id) = transportOrder
    events += event
    Future.unit

  def saveAll(entries: List[(TransportOrder, TransportOrderEvent)]): Future[Unit] =
    entries.foreach { (transportOrder, event) =>
      recorder.record("transportOrder.save")
      store(transportOrder.id) = transportOrder
      events += event
    }
    Future.unit
