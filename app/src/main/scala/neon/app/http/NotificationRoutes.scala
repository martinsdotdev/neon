package neon.app.http

import io.circe.Encoder
import io.circe.syntax.*
import neon.app.auth.AuthenticationService
import neon.app.notification.NotificationEvent
import org.apache.pekko.actor.typed.eventstream.EventStream
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource

import scala.concurrent.ExecutionContext

/** WebSocket endpoint that streams operator-scoped notifications to the
  * mobile client. The handshake validates a Bearer token supplied as the
  * `token` query parameter (RN WebSocket cannot reliably set
  * `Authorization` headers); thereafter the connection receives only
  * events targeting the authenticated user.
  *
  * Events are produced by CQRS projection handlers via the typed event
  * stream (`system.eventStream`). Each WS connection materializes its own
  * `Source.actorRef`, subscribes that ref to the event stream, filters by
  * the authenticated user, and tears down the subscription when the
  * stream terminates.
  *
  * Security note: tokens in query strings are visible in access logs and
  * shouldn't be reflected back to clients or stored beyond the lifetime
  * of the request. In production, configure log redaction for the
  * `token` parameter.
  */
object NotificationRoutes:

  private val SubscriptionBufferSize = 100

  private given Encoder[NotificationEvent] = Encoder.instance {
    case e: NotificationEvent.TaskAssignedToUser =>
      io.circe.Json.obj(
        "type" -> e.topic.asJson,
        "taskId" -> e.taskId.value.toString.asJson,
        "at" -> e.at.toString.asJson
      )
  }

  def apply(
      authService: AuthenticationService
  )(using system: ActorSystem[?], ec: ExecutionContext): Route =
    path("ws" / "notifications"):
      parameter("token"): token =>
        onSuccess(authService.validateSession(token)):
          case Right(context) =>
            handleWebSocketMessages(buildSocketFlow(context.userId))
          case Left(_) =>
            complete(StatusCodes.Unauthorized)

  private def buildSocketFlow(
      userId: neon.common.UserId
  )(using system: ActorSystem[?], ec: ExecutionContext): Flow[Message, Message, ?] =
    val (subscriber, source) =
      ActorSource
        .actorRef[NotificationEvent](
          completionMatcher = PartialFunction.empty,
          failureMatcher = PartialFunction.empty,
          bufferSize = SubscriptionBufferSize,
          overflowStrategy = OverflowStrategy.dropHead
        )
        .preMaterialize()

    system.eventStream ! EventStream.Subscribe(subscriber)

    val outgoing: Source[Message, ?] =
      source
        .filter(_.targetUser == userId)
        .map(event => TextMessage(event.asJson.noSpaces))
        .watchTermination() { (mat, doneFuture) =>
          doneFuture.onComplete { _ =>
            system.eventStream ! EventStream.Unsubscribe(subscriber)
          }
          mat
        }

    // Incoming messages from the client are drained — mobile only sends
    // (eventual) ping frames and we don't need to react to them.
    Flow.fromSinkAndSource(Sink.ignore, outgoing)
