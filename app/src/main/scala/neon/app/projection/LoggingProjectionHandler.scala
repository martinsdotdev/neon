package neon.app.projection

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.Done
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import scala.concurrent.{ExecutionContext, Future}

/** Base class for projection handlers that adds structured logging: DEBUG at event entry, ERROR
  * with stack trace on failure. Subclasses implement `processEvent`.
  */
abstract class LoggingProjectionHandler[E](using
    ExecutionContext
) extends R2dbcHandler[EventEnvelope[E]]
    with LazyLogging:

  final override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done] =
    logger.debug(
      "Processing {} for {}",
      envelope.event.getClass.getSimpleName,
      envelope.persistenceId
    )
    processEvent(session, envelope).recoverWith { case exception =>
      logger.error(
        "Projection failed for {}",
        envelope.persistenceId,
        exception
      )
      Future.failed(exception)
    }

  protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done]
