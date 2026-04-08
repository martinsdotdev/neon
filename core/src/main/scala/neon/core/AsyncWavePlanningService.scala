package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.carrier.AsyncCarrierRepository
import neon.common.{OrderId, WaveId}
import neon.location.AsyncLocationRepository
import neon.order.{Order, OrderLine}
import neon.wave.{OrderGrouping, TaskRequest, WavePlanner}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Async counterpart of [[WavePlanningService]]. */
class AsyncWavePlanningService(
    carrierRepository: AsyncCarrierRepository,
    locationRepository: AsyncLocationRepository,
    waveDispatchAssignmentRepository: AsyncWaveDispatchAssignmentRepository,
    waveDispatchRulesProvider: WaveDispatchRulesProvider,
    waveReleaseService: AsyncWaveReleaseService
)(using ExecutionContext)
    extends LazyLogging:

  def planAndRelease(
      orders: List[Order],
      grouping: OrderGrouping,
      dockAssignments: List[DockCarrierAssignment],
      at: Instant,
      lineResolution: (WaveId, OrderId, OrderLine) => List[TaskRequest] = (waveId, orderId, line) =>
        List(
          TaskRequest(
            waveId,
            orderId,
            line.skuId,
            line.packagingLevel,
            line.quantity
          )
        )
  ): Future[Either[WavePlanningError, WavePlanningResult]] =
    logger.debug(
      "Planning wave for {} orders",
      orders.size: java.lang.Integer
    )
    val rules = waveDispatchRulesProvider.current()

    val carrierIds =
      (orders.flatMap(_.carrierId) ++ dockAssignments.map(_.carrierId)).distinct
    val dockIds = dockAssignments.map(_.dockId).distinct

    for
      carriers <- Future
        .traverse(carrierIds)(id => carrierRepository.findById(id).map(_.map(id -> _)))
        .map(_.flatten.toMap)
      docks <- Future
        .traverse(dockIds)(id => locationRepository.findById(id).map(_.map(id -> _)))
        .map(_.flatten.toMap)
      activeAssignments <- Future
        .traverse(dockIds)(id =>
          waveDispatchAssignmentRepository
            .findActiveByDock(id)
            .map(id -> _)
        )
        .map(_.toMap)
      result <- WaveDispatchValidationPolicy(
        orders = orders,
        dockAssignments = dockAssignments,
        rules = rules,
        carriersById = carriers,
        docksById = docks,
        activeAssignmentsByDock = activeAssignments
      ) match
        case Left(error) => Future.successful(Left(error))
        case Right(_)    =>
          val wavePlan = WavePlanner.plan(orders, grouping, at, lineResolution)
          waveDispatchAssignmentRepository
            .reserveForWave(wavePlan.wave.id, dockAssignments, rules)
            .flatMap:
              case Left(conflict) => Future.successful(Left(conflict))
              case Right(_)       =>
                waveReleaseService
                  .release(wavePlan, at)
                  .map(release =>
                    Right(
                      WavePlanningResult(
                        wavePlan = wavePlan,
                        release = release,
                        dockAssignments = dockAssignments
                      )
                    )
                  )
    yield result
