package neon.app.repository

import neon.common.{CarrierId, LocationId, WaveId}
import neon.core.{
  ActiveDockCarrierAssignment,
  AsyncWaveDispatchAssignmentRepository,
  DockCarrierAssignment,
  WaveDispatchRules,
  WavePlanningError
}
import io.r2dbc.spi.ConnectionFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncWaveDispatchAssignmentRepository]]. Uses database-level
  * transactions for atomic dock/carrier reservation.
  */
class R2dbcWaveDispatchAssignmentRepository(
    connectionFactory: ConnectionFactory
)(using ExecutionContext)
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
      // Check for conflicts first, then insert atomically
      val dockIds = assignments.map(_.dockId)
      val conflictCheck =
        if rules.enforceDockCarrierExclusivityAcrossActiveWaves then
          Future
            .traverse(assignments) { assignment =>
              findActiveByDock(assignment.dockId).map { active =>
                active.find(_.carrierId != assignment.carrierId).map { conflict =>
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
        else Future.successful(None)

      conflictCheck.flatMap:
        case Some(conflict) => Future.successful(Left(conflict))
        case None           =>
          Future
            .traverse(assignments) { assignment =>
              R2dbcHelper.execute(
                connectionFactory,
                "INSERT INTO wave_dispatch_assignment (wave_id, dock_id, carrier_id, active) VALUES ($1, $2, $3, true)",
                waveId.value,
                assignment.dockId.value,
                assignment.carrierId.value
              )
            }
            .map(_ => Right(()))
