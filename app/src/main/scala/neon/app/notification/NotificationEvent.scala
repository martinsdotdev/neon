package neon.app.notification

import neon.common.{TaskId, UserId}

import java.time.Instant

/** Events broadcast to mobile clients via the WebSocket notification channel.
  *
  * Producers (CQRS projection handlers) publish these to `system.eventStream`; consumers (the WS
  * session flow created in NotificationRoutes) subscribe to the trait and filter by user.
  *
  * The trait is a regular sealed hierarchy — `eventStream.publish` uses Java `Class[_]` matching,
  * so consumers subscribe to either the concrete subtype or the trait itself. JSON shape on the
  * wire is flat with a `type` discriminator.
  */
sealed trait NotificationEvent:

  /** The user whose notification stream should receive this event. */
  def targetUser: UserId

  /** Wall-clock instant the originating domain event occurred. */
  def at: Instant

  /** Discriminator used by mobile clients to route the event. */
  def topic: String

object NotificationEvent:

  /** A task has been assigned to `targetUser`. Mobile picker invalidates the assigned-tasks query
    * so the new row appears immediately.
    */
  final case class TaskAssignedToUser(
      targetUser: UserId,
      taskId: TaskId,
      at: Instant
  ) extends NotificationEvent:
    val topic: String = "task.assigned"
