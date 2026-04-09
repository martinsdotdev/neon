package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.consolidationgroup.ConsolidationGroupEvent

import java.time.Instant

class ConsolidationGroupProjectionHandlerSuite extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler =
    ConsolidationGroupProjectionHandler()

  describe("ConsolidationGroupProjectionHandler"):

    it(
      "inserts into consolidation_group_by_wave " +
        "on ConsolidationGroupCreated"
    ):
      val consolidationGroupId = ConsolidationGroupId()
      val waveId = WaveId()
      val orderIds = List(OrderId(), OrderId())

      val event =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = consolidationGroupId,
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
              s"ConsolidationGroup|${consolidationGroupId.value}",
              "ConsolidationGroup"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM consolidation_group_by_wave " +
          "WHERE consolidation_group_id = " +
          s"'${consolidationGroupId.value}'"
      )
      assert(count == 1L)

    it(
      "updates state to Picked " +
        "on ConsolidationGroupPicked"
    ):
      val consolidationGroupId = ConsolidationGroupId()
      val waveId = WaveId()

      val created =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = consolidationGroupId,
          waveId = waveId,
          orderIds = List(OrderId()),
          occurredAt = Instant.now()
        )

      val picked =
        ConsolidationGroupEvent.ConsolidationGroupPicked(
          consolidationGroupId = consolidationGroupId,
          waveId = waveId,
          occurredAt = Instant.now()
        )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              created,
              s"ConsolidationGroup|${consolidationGroupId.value}",
              "ConsolidationGroup"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              picked,
              s"ConsolidationGroup|${consolidationGroupId.value}",
              "ConsolidationGroup"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM consolidation_group_by_wave " +
          "WHERE consolidation_group_id = " +
          s"'${consolidationGroupId.value}' " +
          "AND state = 'Picked'"
      )
      assert(count == 1L)

    it(
      "updates state to Completed " +
        "on ConsolidationGroupCompleted"
    ):
      val consolidationGroupId = ConsolidationGroupId()
      val waveId = WaveId()

      val created =
        ConsolidationGroupEvent.ConsolidationGroupCreated(
          consolidationGroupId = consolidationGroupId,
          waveId = waveId,
          orderIds = List(OrderId()),
          occurredAt = Instant.now()
        )

      val completed =
        ConsolidationGroupEvent.ConsolidationGroupCompleted(
          consolidationGroupId = consolidationGroupId,
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
              s"ConsolidationGroup|${consolidationGroupId.value}",
              "ConsolidationGroup"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              completed,
              s"ConsolidationGroup|${consolidationGroupId.value}",
              "ConsolidationGroup"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) " +
          "FROM consolidation_group_by_wave " +
          "WHERE consolidation_group_id = " +
          s"'${consolidationGroupId.value}' " +
          "AND state = 'Completed'"
      )
      assert(count == 1L)
