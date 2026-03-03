package neon.app

import neon.task.TaskEvent
import neon.transportorder.{TransportOrder, TransportOrderEvent}

import java.time.Instant

object RoutingPolicy:
  def apply(
      event: TaskEvent.TaskCompleted,
      at: Instant
  ): Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)] =
    event.handlingUnitId.map { handlingUnitId =>
      TransportOrder.create(handlingUnitId, event.destinationLocationId, at)
    }
