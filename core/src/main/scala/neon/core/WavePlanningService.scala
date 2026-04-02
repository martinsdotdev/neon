package neon.core

import neon.carrier.CarrierRepository
import neon.common.{OrderId, WaveId}
import neon.location.LocationRepository
import neon.order.{Order, OrderLine}
import neon.wave.{OrderGrouping, TaskRequest, WavePlan, WavePlanner}

import java.time.Instant

/** Result of wave planning orchestration with validated dock/carrier assignments.
  *
  * @param wavePlan
  *   planned wave and task requests
  * @param release
  *   persisted release result (wave, tasks, consolidation groups)
  * @param dockAssignments
  *   dock/carrier assignments reserved for this wave
  */
case class WavePlanningResult(
    wavePlan: WavePlan,
    release: WaveReleaseResult,
    dockAssignments: List[DockCarrierAssignment]
)

/** Orchestrates wave planning and release with pre-release carrier/dock validation.
  *
  * Validation happens before release. Reservation of dock/carrier assignments is required to be
  * atomic in the repository implementation.
  */
class WavePlanningService(
    carrierRepository: CarrierRepository,
    locationRepository: LocationRepository,
    waveDispatchAssignmentRepository: WaveDispatchAssignmentRepository,
    waveDispatchRulesProvider: WaveDispatchRulesProvider,
    waveReleaseService: WaveReleaseService
):
  /** Plans and releases a wave after validating carrier/dock constraints.
    *
    * Steps:
    *   1. load current rules
    *   2. validate carrier/dock assignments
    *   3. create wave plan
    *   4. reserve assignments atomically
    *   5. release the wave
    */
  def planAndRelease(
      orders: List[Order],
      grouping: OrderGrouping,
      dockAssignments: List[DockCarrierAssignment],
      at: Instant,
      lineResolution: (WaveId, OrderId, OrderLine) => List[TaskRequest] = (waveId, orderId, line) =>
        List(TaskRequest(waveId, orderId, line.skuId, line.packagingLevel, line.quantity))
  ): Either[WavePlanningError, WavePlanningResult] =
    val rules = waveDispatchRulesProvider.current()

    val carrierIds = (orders.flatMap(_.carrierId) ++ dockAssignments.map(_.carrierId)).distinct
    val carriersById =
      carrierIds.flatMap(id => carrierRepository.findById(id).map(id -> _)).toMap

    val dockIds = dockAssignments.map(_.dockId).distinct
    val docksById = dockIds.flatMap(id => locationRepository.findById(id).map(id -> _)).toMap
    val activeAssignmentsByDock =
      dockIds.map(id => id -> waveDispatchAssignmentRepository.findActiveByDock(id)).toMap

    WaveDispatchValidationPolicy(
      orders = orders,
      dockAssignments = dockAssignments,
      rules = rules,
      carriersById = carriersById,
      docksById = docksById,
      activeAssignmentsByDock = activeAssignmentsByDock
    ) match
      case Left(error) => Left(error)
      case Right(_)    =>
        val wavePlan = WavePlanner.plan(orders, grouping, at, lineResolution)
        waveDispatchAssignmentRepository.reserveForWave(
          wavePlan.wave.id,
          dockAssignments,
          rules
        ) match
          case Left(conflict) => Left(conflict)
          case Right(_)       =>
            val release = waveReleaseService.release(wavePlan, at)
            Right(
              WavePlanningResult(
                wavePlan = wavePlan,
                release = release,
                dockAssignments = dockAssignments
              )
            )
