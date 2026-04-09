package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.consolidationgroup.ConsolidationGroupEvent
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class ConsolidationGroupProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler =
    ConsolidationGroupProjectionHandler()

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
      entityType = "ConsolidationGroup",
      slice = 0
    )

  describe("ConsolidationGroupProjectionHandler") {

    it(
      "should insert into consolidation_group_by_wave on ConsolidationGroupCreated"
    ) {
      val cgId = ConsolidationGroupId()
      val waveId = WaveId()
      val orderIds = List(OrderId(), OrderId())

      val event =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = cgId,
          waveId = waveId,
          orderIds = orderIds,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"ConsolidationGroup|${cgId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM consolidation_group_by_wave WHERE consolidation_group_id = '${cgId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Picked on ConsolidationGroupPicked"
    ) {
      val cgId = ConsolidationGroupId()
      val waveId = WaveId()

      val created =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = cgId,
          waveId = waveId,
          orderIds = List(OrderId()),
          occurredAt = Instant.now()
        )

      val picked =
        ConsolidationGroupEvent.ConsolidationGroupPicked(
          consolidationGroupId = cgId,
          waveId = waveId,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"ConsolidationGroup|${cgId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              picked,
              s"ConsolidationGroup|${cgId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM consolidation_group_by_wave WHERE consolidation_group_id = '${cgId.value}' AND state = 'Picked'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Completed on ConsolidationGroupCompleted"
    ) {
      val cgId = ConsolidationGroupId()
      val waveId = WaveId()

      val created =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = cgId,
          waveId = waveId,
          orderIds = List(OrderId()),
          occurredAt = Instant.now()
        )

      val completed =
        ConsolidationGroupEvent.ConsolidationGroupCompleted(
          consolidationGroupId = cgId,
          waveId = waveId,
          workstationId = WorkstationId(),
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"ConsolidationGroup|${cgId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              completed,
              s"ConsolidationGroup|${cgId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM consolidation_group_by_wave WHERE consolidation_group_id = '${cgId.value}' AND state = 'Completed'"
      )
      assert(count == 1L)
    }
  }
