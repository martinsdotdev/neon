package neon.app

import neon.transportorder.{TransportOrder, TransportOrderEvent}

import java.time.Instant

object TransportOrderCancellationPolicy:
  def apply(
      transportOrders: List[TransportOrder],
      at: Instant
  ): List[(TransportOrder.Cancelled, TransportOrderEvent.TransportOrderCancelled)] =
    transportOrders.collect:
      case t: TransportOrder.Pending => t.cancel(at)
