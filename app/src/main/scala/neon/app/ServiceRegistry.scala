package neon.app

import neon.app.auth.*
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
)(using Timeout, ExecutionContext):

  private given ActorSystem[?] = system

  // --- Actor-backed repositories ---

  val waveRepository = PekkoWaveRepository(system)
  val taskRepository = PekkoTaskRepository(system, connectionFactory)
  val consolidationGroupRepository =
    PekkoConsolidationGroupRepository(system, connectionFactory)
  val transportOrderRepository =
    PekkoTransportOrderRepository(system, connectionFactory)
  val handlingUnitRepository = PekkoHandlingUnitRepository(system)
  val workstationRepository =
    PekkoWorkstationRepository(system, connectionFactory)
  val slotRepository = PekkoSlotRepository(system, connectionFactory)
  val inventoryRepository =
    PekkoInventoryRepository(system, connectionFactory)

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

  val taskLifecycleService = AsyncTaskLifecycleService(
    taskRepository,
    userRepository
  )

  val workstationLifecycleService =
    AsyncWorkstationLifecycleService(workstationRepository)

  val transportOrderCancellationService =
    AsyncTransportOrderCancellationService(
      transportOrderRepository
    )

  val consolidationGroupCancellationService =
    AsyncConsolidationGroupCancellationService(
      consolidationGroupRepository
    )

  val handlingUnitLifecycleService =
    AsyncHandlingUnitLifecycleService(handlingUnitRepository)

  val slotService = AsyncSlotService(slotRepository)

  val inventoryService =
    AsyncInventoryService(inventoryRepository)

  // --- Authentication ---

  val passwordHasher = PasswordHasher()
  val sessionRepository =
    R2dbcSessionRepository(connectionFactory)
  val permissionRepository =
    R2dbcPermissionRepository(connectionFactory)
  val authenticationService = AuthenticationService(
    userRepository,
    sessionRepository,
    permissionRepository,
    passwordHasher
  )
