package neon.app

import neon.app.repository.*
import neon.consolidationgroup.PekkoConsolidationGroupRepository
import neon.core.*
import neon.handlingunit.PekkoHandlingUnitRepository
import neon.inventory.PekkoInventoryRepository
import neon.slot.PekkoSlotRepository
import neon.task.PekkoTaskRepository
import neon.transportorder.PekkoTransportOrderRepository
import neon.wave.PekkoWaveRepository
import neon.workstation.PekkoWorkstationRepository

import io.r2dbc.spi.ConnectionFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.ExecutionContext

/** Wires all async repository implementations and service instances. Constructed by the
  * [[Guardian]] actor at startup.
  */
class ServiceRegistry(
    system: ActorSystem[?],
    connectionFactory: ConnectionFactory
)(using Timeout):

  private given ExecutionContext = system.executionContext

  // --- Actor-backed repositories ---

  val waveRepository = PekkoWaveRepository(system)
  val taskRepository = PekkoTaskRepository(system)
  val consolidationGroupRepository =
    PekkoConsolidationGroupRepository(system)
  val transportOrderRepository = PekkoTransportOrderRepository(system)
  val handlingUnitRepository = PekkoHandlingUnitRepository(system)
  val workstationRepository = PekkoWorkstationRepository(system)
  val slotRepository = PekkoSlotRepository(system)
  val inventoryRepository = PekkoInventoryRepository(system)

  // --- Reference data repositories (R2DBC) ---

  val locationRepository = R2dbcLocationRepository(connectionFactory)
  val carrierRepository = R2dbcCarrierRepository(connectionFactory)
  val orderRepository = R2dbcOrderRepository(connectionFactory)
  val skuRepository = R2dbcSkuRepository(connectionFactory)
  val userRepository = R2dbcUserRepository(connectionFactory)
  val waveDispatchAssignmentRepository =
    R2dbcWaveDispatchAssignmentRepository(connectionFactory)

  // --- Services ---

  val waveReleaseService = AsyncWaveReleaseService(
    waveRepository,
    taskRepository,
    consolidationGroupRepository
  )

  val taskCompletionService = AsyncTaskCompletionService(
    taskRepository,
    waveRepository,
    consolidationGroupRepository,
    transportOrderRepository,
    VerificationProfile.disabled
  )

  val waveCancellationService = AsyncWaveCancellationService(
    waveRepository,
    taskRepository,
    transportOrderRepository,
    consolidationGroupRepository
  )

  val transportOrderConfirmationService =
    AsyncTransportOrderConfirmationService(
      transportOrderRepository,
      handlingUnitRepository,
      taskRepository,
      consolidationGroupRepository
    )

  val consolidationGroupCompletionService =
    AsyncConsolidationGroupCompletionService(
      consolidationGroupRepository,
      workstationRepository
    )

  val workstationAssignmentService = AsyncWorkstationAssignmentService(
    consolidationGroupRepository,
    workstationRepository
  )

  val wavePlanningService = AsyncWavePlanningService(
    carrierRepository,
    locationRepository,
    waveDispatchAssignmentRepository,
    DefaultWaveDispatchRulesProvider(),
    waveReleaseService
  )
