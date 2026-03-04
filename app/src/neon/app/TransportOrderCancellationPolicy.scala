package neon.app

import neon.transportorder.{TransportOrder, TransportOrderEvent}

import java.time.Instant

/** Cancels all [[TransportOrder.Pending]] transport orders.
  *
  * Orders already in [[TransportOrder.Confirmed]] or [[TransportOrder.Cancelled]] are left
  * unchanged.
  */
object TransportOrderCancellationPolicy:

  /** Cancels each transport order still in Pending state.
    *
    * @param transportOrders
    *   the transport orders to evaluate
    * @param at
    *   instant of the cancellation
    * @return
    *   cancelled orders paired with their cancellation events
    */
  def apply(
      transportOrders: List[TransportOrder],
      at: Instant
  ): List[(TransportOrder.Cancelled, TransportOrderEvent.TransportOrderCancelled)] =
    transportOrders.collect:
      case t: TransportOrder.Pending => t.cancel(at)
