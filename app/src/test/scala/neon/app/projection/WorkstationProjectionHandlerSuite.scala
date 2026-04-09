package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.workstation.{Workstation, WorkstationActor, WorkstationEvent, WorkstationType}

import java.time.Instant

class WorkstationProjectionHandlerSuite
    extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = WorkstationProjectionHandler()

  describe("WorkstationProjectionHandler"):

    it(
      "inserts into workstation_by_type_and_state " +
        "on Initialized"
    ):
      val workstationId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = workstationId,
        workstationType = WorkstationType.PutWall,
        slotCount = 8
      )

      val event =
        WorkstationActor.Initialized(workstation)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"Workstation|${workstationId.value}",
              "Workstation"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM workstation_by_type_and_state " +
          "WHERE workstation_id = " +
          s"'${workstationId.value}'"
      )
      assert(count == 1L)

    it("updates state to Idle on WorkstationEnabled"):
      val workstationId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = workstationId,
        workstationType = WorkstationType.PackStation,
        slotCount = 4
      )

      val initialized =
        WorkstationActor.Initialized(workstation)
      val enabled = WorkstationActor.DomainEvent(
        WorkstationEvent.WorkstationEnabled(
          workstationId = workstationId,
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
              s"Workstation|${workstationId.value}",
              "Workstation"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              enabled,
              s"Workstation|${workstationId.value}",
              "Workstation"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM workstation_by_type_and_state " +
          "WHERE workstation_id = " +
          s"'${workstationId.value}' " +
          "AND state = 'Idle'"
      )
      assert(count == 1L)

    it(
      "updates state to Active on WorkstationAssigned"
    ):
      val workstationId = WorkstationId()
      val workstation = Workstation.Disabled(
        id = workstationId,
        workstationType = WorkstationType.PutWall,
        slotCount = 6
      )

      val initialized =
        WorkstationActor.Initialized(workstation)
      val assigned = WorkstationActor.DomainEvent(
        WorkstationEvent.WorkstationAssigned(
          workstationId = workstationId,
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
              s"Workstation|${workstationId.value}",
              "Workstation"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              assigned,
              s"Workstation|${workstationId.value}",
              "Workstation"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM workstation_by_type_and_state " +
          "WHERE workstation_id = " +
          s"'${workstationId.value}' " +
          "AND state = 'Active'"
      )
      assert(count == 1L)
