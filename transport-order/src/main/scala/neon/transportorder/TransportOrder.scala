package neon.transportorder

import com.fasterxml.jackson.annotation.JsonTypeInfo
import neon.common.{HandlingUnitId, LocationId, TransportOrderId}

import java.time.Instant

/** Typestate-encoded transport order aggregate for routing a handling unit to a destination.
  *
  * Lifecycle: [[Pending]] -> [[Confirmed]] | [[Cancelled]]. Created by routing policies when a task
  * completes, representing the temporal gap between task completion and operator confirmation at
  * the destination.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed trait TransportOrder:
  /** The transport order identifier. */
  def id: TransportOrderId

  /** The handling unit being transported. */
  def handlingUnitId: HandlingUnitId

  /** The target location for delivery. */
  def destination: LocationId

/** Factory and state definitions for [[TransportOrder]]. */
object TransportOrder:
  /** Creates a pending transport order for routing a handling unit to a destination.
    *
    * @param handlingUnitId
    *   the handling unit to transport
    * @param destination
    *   target location
    * @param at
    *   instant of creation
    * @return
    *   pending transport order and creation event
    */
  def create(
      handlingUnitId: HandlingUnitId,
      destination: LocationId,
      at: Instant
  ): (Pending, TransportOrderEvent.TransportOrderCreated) =
    val id = TransportOrderId()
    val pending = Pending(id, handlingUnitId, destination)
    val event = TransportOrderEvent.TransportOrderCreated(id, handlingUnitId, destination, at)
    (pending, event)

  /** A transport order awaiting operator confirmation at the destination. */
  case class Pending(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder:
    /** Confirms delivery, transitioning from [[Pending]] to [[Confirmed]].
      *
      * @param at
      *   instant of confirmation
      * @return
      *   confirmed state and confirmation event
      */
    def confirm(at: Instant): (Confirmed, TransportOrderEvent.TransportOrderConfirmed) =
      val confirmed = Confirmed(id, handlingUnitId, destination)
      val event =
        TransportOrderEvent.TransportOrderConfirmed(id, handlingUnitId, destination, at)
      (confirmed, event)

    /** Cancels this transport order, transitioning from [[Pending]] to [[Cancelled]].
      *
      * @param at
      *   instant of cancellation
      * @return
      *   cancelled state and cancellation event
      */
    def cancel(at: Instant): (Cancelled, TransportOrderEvent.TransportOrderCancelled) =
      val cancelled = Cancelled(id, handlingUnitId, destination)
      val event =
        TransportOrderEvent.TransportOrderCancelled(id, handlingUnitId, destination, at)
      (cancelled, event)

  /** A transport order whose delivery has been confirmed. Terminal state. */
  case class Confirmed(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder

  /** A transport order that was cancelled before confirmation. Terminal state. */
  case class Cancelled(
      id: TransportOrderId,
      handlingUnitId: HandlingUnitId,
      destination: LocationId
  ) extends TransportOrder
