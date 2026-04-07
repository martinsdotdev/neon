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
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}

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
        val stmt = connection
          .createStatement(
            "SELECT wave_id, dock_id, carrier_id FROM wave_dispatch_assignment WHERE dock_id = $1 AND active = true FOR UPDATE"
          )
          .bind(0, assignment.dockId.value)
        collectRows(stmt.execute()) { row =>
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
      .traverse(assignments) { assignment =>
        val stmt = connection
          .createStatement(
            "INSERT INTO wave_dispatch_assignment (wave_id, dock_id, carrier_id, active) VALUES ($1, $2, $3, true)"
          )
          .bind(0, waveId.value)
          .bind(1, assignment.dockId.value)
          .bind(2, assignment.carrierId.value)
        toFuture(stmt.execute()).map(_ => ())
      }
      .map(_ => ())

  private def collectRows[T](
      resultPublisher: Publisher[? <: io.r2dbc.spi.Result]
  )(mapRow: Row => T): Future[List[T]] =
    val promise = Promise[List[T]]()
    val rows = scala.collection.mutable.ListBuffer[T]()
    resultPublisher.subscribe(new Subscriber[io.r2dbc.spi.Result]:
      override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
      override def onNext(result: io.r2dbc.spi.Result): Unit =
        result
          .map((row, _) => rows += mapRow(row))
          .subscribe(new Subscriber[Object]:
            override def onSubscribe(s: Subscription): Unit =
              s.request(Long.MaxValue)
            override def onNext(t: Object): Unit = ()
            override def onError(t: Throwable): Unit = promise.tryFailure(t)
            override def onComplete(): Unit = ())
      override def onError(t: Throwable): Unit = promise.tryFailure(t)
      override def onComplete(): Unit = promise.trySuccess(rows.toList))
    promise.future

  private def toFuture[T](publisher: Publisher[T]): Future[T] =
    val promise = Promise[T]()
    publisher.subscribe(new Subscriber[T]:
      override def onSubscribe(s: Subscription): Unit = s.request(1)
      override def onNext(t: T): Unit = promise.trySuccess(t)
      override def onError(t: Throwable): Unit = promise.tryFailure(t)
      override def onComplete(): Unit = ())
    promise.future
