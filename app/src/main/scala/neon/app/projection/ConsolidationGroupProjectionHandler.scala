package neon.app.projection

import neon.consolidationgroup.ConsolidationGroupEvent

import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.r2dbc.scaladsl.{R2dbcHandler, R2dbcSession}

import scala.concurrent.{ExecutionContext, Future}

/** Populates `consolidation_group_by_wave` from consolidation group events. */
class ConsolidationGroupProjectionHandler(using ExecutionContext)
    extends R2dbcHandler[EventEnvelope[ConsolidationGroupEvent]]:

  override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[ConsolidationGroupEvent]
  ): Future[org.apache.pekko.Done] =
    envelope.event match
      case e: ConsolidationGroupEvent.ConsolidationGroupCreated =>
        val stmt = session
          .createStatement(
            """INSERT INTO consolidation_group_by_wave
              |  (consolidation_group_id, wave_id, order_ids, state)
              |VALUES ($1, $2, $3, $4)
              |ON CONFLICT (consolidation_group_id) DO UPDATE SET state = $4""".stripMargin
          )
          .bind(0, e.consolidationGroupId.value)
          .bind(1, e.waveId.value)
          .bind(2, e.orderIds.map(_.value).toArray)
          .bind(3, "Created")
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)

      case e =>
        val state = e match
          case _: ConsolidationGroupEvent.ConsolidationGroupPicked =>
            "Picked"
          case _: ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation =>
            "ReadyForWorkstation"
          case _: ConsolidationGroupEvent.ConsolidationGroupAssigned =>
            "Assigned"
          case _: ConsolidationGroupEvent.ConsolidationGroupCompleted =>
            "Completed"
          case _: ConsolidationGroupEvent.ConsolidationGroupCancelled =>
            "Cancelled"
          case _: ConsolidationGroupEvent.ConsolidationGroupCreated =>
            "Created"
        val stmt = session
          .createStatement(
            "UPDATE consolidation_group_by_wave SET state = $1 WHERE consolidation_group_id = $2"
          )
          .bind(0, state)
          .bind(1, e.consolidationGroupId.value)
        session.updateOne(stmt).map(_ => org.apache.pekko.Done)
