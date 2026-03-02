package neon.app

import neon.common.LocationId
import neon.consolidationgroup.{TransportOrder, TransportOrderEvent}
import neon.task.TaskEvent
import java.time.Instant

object RoutingPolicy:
  def evaluate(
      event: TaskEvent.TaskCompleted,
      destination: LocationId,
      at: Instant
  ): Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)] =
    event.handlingUnitId.map { handlingUnitId =>
      TransportOrder.create(handlingUnitId, destination, at)
    }
