package neon.app

import neon.common.LocationId
import neon.task.TaskEvent
import neon.transportorder.{TransportOrder, TransportOrderEvent}

import java.time.Instant

object RoutingPolicy:
  def apply(
      event: TaskEvent.TaskCompleted,
      destination: LocationId,
      at: Instant
  ): Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)] =
    event.handlingUnitId.map { handlingUnitId =>
      TransportOrder.create(handlingUnitId, destination, at)
    }
