package neon.app

import neon.task.TaskEvent
import neon.transportorder.{TransportOrder, TransportOrderEvent}

import java.time.Instant

/** Creates a [[TransportOrder.Pending]] to route a handling unit to its destination when a task
  * completes with an associated handling unit.
  *
  * Returns [[None]] when the completed task has no handling unit (e.g. REPLENISH or TRANSFER
  * without HU).
  */
object RoutingPolicy:

  /** Creates a transport order for the completed task's handling unit.
    *
    * @param event
    *   the task completion event carrying handling unit and destination
    * @param at
    *   instant of the transport order creation
    * @return
    *   pending transport order and creation event, or [[None]] if the task has no handling unit
    */
  def apply(
      event: TaskEvent.TaskCompleted,
      at: Instant
  ): Option[(TransportOrder.Pending, TransportOrderEvent.TransportOrderCreated)] =
    event.handlingUnitId.map { handlingUnitId =>
      TransportOrder.create(handlingUnitId, event.destinationLocationId, at)
    }
