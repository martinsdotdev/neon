package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.handlingunit.{HandlingUnit, HandlingUnitActor, HandlingUnitEvent}
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class HandlingUnitProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = HandlingUnitProjectionHandler()

  private def withSession(
      f: R2dbcSession => Unit
  ): Unit =
    val connection =
      Mono.from(connectionFactory.create()).block()
    try
      val session = new R2dbcSession(connection)(using
        system.executionContext,
        system
      )
      f(session)
    finally Mono.from(connection.close()).block()

  private def envelope[E](
      event: E,
      persistenceId: String
  ): EventEnvelope[E] =
    new EventEnvelope[E](
      offset = TimestampOffset.Zero,
      persistenceId = persistenceId,
      sequenceNr = 1L,
      eventOption = Some(event),
      timestamp = System.currentTimeMillis(),
      eventMetadata = None,
      entityType = "HandlingUnit",
      slice = 0
    )

  describe("HandlingUnitProjectionHandler") {

    it(
      "should insert into handling_unit_lookup on Initialized"
    ) {
      val huId = HandlingUnitId()
      val locationId = LocationId()
      val handlingUnit = HandlingUnit.PickCreated(
        id = huId,
        packagingLevel = PackagingLevel.Case,
        currentLocation = locationId
      )

      val event =
        HandlingUnitActor.Initialized(handlingUnit)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"HandlingUnit|${huId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM handling_unit_lookup WHERE handling_unit_id = '${huId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should update state and location on HandlingUnitMovedToBuffer"
    ) {
      val huId = HandlingUnitId()
      val locationId = LocationId()
      val bufferLocation = LocationId()

      val handlingUnit = HandlingUnit.PickCreated(
        id = huId,
        packagingLevel = PackagingLevel.Pallet,
        currentLocation = locationId
      )

      val initialized =
        HandlingUnitActor.Initialized(handlingUnit)
      val movedToBuffer = HandlingUnitActor.DomainEvent(
        HandlingUnitEvent.HandlingUnitMovedToBuffer(
          handlingUnitId = huId,
          locationId = bufferLocation,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"HandlingUnit|${huId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              movedToBuffer,
              s"HandlingUnit|${huId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM handling_unit_lookup WHERE handling_unit_id = '${huId.value}' AND state = 'InBuffer'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Empty on HandlingUnitEmptied"
    ) {
      val huId = HandlingUnitId()
      val locationId = LocationId()

      val handlingUnit = HandlingUnit.PickCreated(
        id = huId,
        packagingLevel = PackagingLevel.Case,
        currentLocation = locationId
      )

      val initialized =
        HandlingUnitActor.Initialized(handlingUnit)
      val emptied = HandlingUnitActor.DomainEvent(
        HandlingUnitEvent.HandlingUnitEmptied(
          handlingUnitId = huId,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"HandlingUnit|${huId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              emptied,
              s"HandlingUnit|${huId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM handling_unit_lookup WHERE handling_unit_id = '${huId.value}' AND state = 'Empty'"
      )
      assert(count == 1L)
    }
  }
