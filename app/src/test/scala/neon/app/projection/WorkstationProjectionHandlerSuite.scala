package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.workstation.{Workstation, WorkstationActor, WorkstationEvent, WorkstationType}
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext

import java.time.Instant

class WorkstationProjectionHandlerSuite extends PostgresContainerSuite:

  private given ExecutionContext = system.executionContext

  private val handler = WorkstationProjectionHandler()

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
      entityType = "Workstation",
      slice = 0
    )

  describe("WorkstationProjectionHandler") {

    it(
      "should insert into workstation_by_type_and_state on Initialized"
    ) {
      val wsId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = wsId,
        workstationType = WorkstationType.PutWall,
        slotCount = 8
      )

      val event = WorkstationActor.Initialized(workstation)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Workstation|${wsId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM workstation_by_type_and_state WHERE workstation_id = '${wsId.value}'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Idle on WorkstationEnabled"
    ) {
      val wsId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = wsId,
        workstationType = WorkstationType.PackStation,
        slotCount = 4
      )

      val initialized =
        WorkstationActor.Initialized(workstation)
      val enabled = WorkstationActor.DomainEvent(
        WorkstationEvent.WorkstationEnabled(
          workstationId = wsId,
          workstationType = WorkstationType.PackStation,
          slotCount = 4,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"Workstation|${wsId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              enabled,
              s"Workstation|${wsId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM workstation_by_type_and_state WHERE workstation_id = '${wsId.value}' AND state = 'Idle'"
      )
      assert(count == 1L)
    }

    it(
      "should update state to Active on WorkstationAssigned"
    ) {
      val wsId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = wsId,
        workstationType = WorkstationType.PutWall,
        slotCount = 6
      )

      val initialized =
        WorkstationActor.Initialized(workstation)
      val assigned = WorkstationActor.DomainEvent(
        WorkstationEvent.WorkstationAssigned(
          workstationId = wsId,
          workstationType = WorkstationType.PutWall,
          consolidationGroupId = ConsolidationGroupId(),
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"Workstation|${wsId.value}"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              assigned,
              s"Workstation|${wsId.value}"
            )
          )
          .futureValue
      }

      val count = queryCount(
        s"SELECT COUNT(*) FROM workstation_by_type_and_state WHERE workstation_id = '${wsId.value}' AND state = 'Active'"
      )
      assert(count == 1L)
    }
  }
