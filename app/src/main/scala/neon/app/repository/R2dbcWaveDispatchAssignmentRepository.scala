package neon.app.repository

import neon.common.{CarrierId, LocationId, WaveId}
import neon.core.{
  ActiveDockCarrierAssignment,
  AsyncWaveDispatchAssignmentRepository,
  DockCarrierAssignment,
  WaveDispatchRules,
  WavePlanningError
}
import io.r2dbc.spi.{Connection, ConnectionFactory, Row}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncWaveDispatchAssignmentRepository]]. Uses database-level
  * transactions for atomic dock/carrier reservation.
  */
class R2dbcWaveDispatchAssignmentRepository(
    connectionFactory: ConnectionFactory
)(using system: ActorSystem[?], ec: ExecutionContext)
    extends AsyncWaveDispatchAssignmentRepository:

  def findActiveByDock(
      dockId: LocationId
  ): Future[List[ActiveDockCarrierAssignment]] =
    R2dbcHelper.queryList(
      connectionFactory,
      "SELECT wave_id, dock_id, carrier_id FROM wave_dispatch_assignment WHERE dock_id = $1 AND active = true",
      dockId.value
    ) { row =>
      ActiveDockCarrierAssignment(
        waveId = WaveId(row.get("wave_id", classOf[UUID])),
        dockId = LocationId(row.get("dock_id", classOf[UUID])),
        carrierId = CarrierId(row.get("carrier_id", classOf[UUID]))
      )
    }

  def reserveForWave(
      waveId: WaveId,
      assignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules
  ): Future[Either[WavePlanningError.DockConflict, Unit]] =
    if assignments.isEmpty then Future.successful(Right(()))
    else
      R2dbcHelper.withTransaction(connectionFactory) { connection =>
        checkConflictsAndInsert(connection, waveId, assignments, rules)
      }

  private def checkConflictsAndInsert(
      connection: Connection,
      waveId: WaveId,
      assignments: List[DockCarrierAssignment],
      rules: WaveDispatchRules
  ): Future[Either[WavePlanningError.DockConflict, Unit]] =
    if !rules.enforceDockCarrierExclusivityAcrossActiveWaves then
      insertAll(connection, waveId, assignments).map(_ => Right(()))
    else
      // SELECT FOR UPDATE locks rows, preventing concurrent reservations
      checkConflicts(connection, assignments).flatMap:
        case Some(conflict) => Future.successful(Left(conflict))
        case None           =>
          insertAll(connection, waveId, assignments).map(_ => Right(()))

  private def checkConflicts(
      connection: Connection,
      assignments: List[DockCarrierAssignment]
  ): Future[Option[WavePlanningError.DockConflict]] =
    Future
      .traverse(assignments) { assignment =>
        queryRows(
          connection,
          "SELECT wave_id, dock_id, carrier_id FROM wave_dispatch_assignment WHERE dock_id = $1 AND active = true FOR UPDATE",
          assignment.dockId.value
        ) { row =>
          ActiveDockCarrierAssignment(
            waveId = WaveId(row.get("wave_id", classOf[UUID])),
            dockId = LocationId(row.get("dock_id", classOf[UUID])),
            carrierId = CarrierId(row.get("carrier_id", classOf[UUID]))
          )
        }.map { active =>
          active
            .find(_.carrierId != assignment.carrierId)
            .map { conflict =>
              WavePlanningError.DockConflict(
                dockId = assignment.dockId,
                requestedCarrierId = assignment.carrierId,
                activeCarrierId = conflict.carrierId,
                activeWaveId = conflict.waveId
              )
            }
        }
      }
      .map(_.flatten.headOption)

  private def insertAll(
      connection: Connection,
      waveId: WaveId,
      assignments: List[DockCarrierAssignment]
  ): Future[Unit] =
    Future
      .sequence(assignments.map { assignment =>
        val stmt = connection
          .createStatement(
            "INSERT INTO wave_dispatch_assignment (wave_id, dock_id, carrier_id, active) VALUES ($1, $2, $3, true)"
          )
          .bind(0, waveId.value)
          .bind(1, assignment.dockId.value)
          .bind(2, assignment.carrierId.value)
        Source.fromPublisher(stmt.execute()).runWith(Sink.head).map(_ => ())
      })
      .map(_ => ())

  private def queryRows[T](
      connection: Connection,
      sql: String,
      param: Any
  )(mapRow: Row => T): Future[List[T]] =
    val stmt = connection.createStatement(sql).bind(0, param)
    Source
      .fromPublisher(stmt.execute())
      .flatMapConcat { result =>
        Source.fromPublisher(result.map((row, _) => mapRow(row)))
      }
      .runWith(Sink.seq)
      .map(_.toList)
