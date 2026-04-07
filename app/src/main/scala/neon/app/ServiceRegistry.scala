package neon.app

import neon.consolidationgroup.PekkoConsolidationGroupRepository
import neon.core.*
import neon.handlingunit.PekkoHandlingUnitRepository
import neon.inventory.PekkoInventoryRepository
import neon.slot.PekkoSlotRepository
import neon.task.PekkoTaskRepository
import neon.transportorder.PekkoTransportOrderRepository
import neon.wave.PekkoWaveRepository
import neon.workstation.PekkoWorkstationRepository
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.ExecutionContext

/** Wires all async repository implementations and service instances. Constructed by the
  * [[Guardian]] actor at startup.
  */
class ServiceRegistry(system: ActorSystem[?])(using Timeout):

  private given ExecutionContext = system.executionContext

  // --- Repositories (each initializes its own sharding entity type) ---

  val waveRepository = PekkoWaveRepository(system)
  val taskRepository = PekkoTaskRepository(system)
  val consolidationGroupRepository = PekkoConsolidationGroupRepository(system)
  val transportOrderRepository = PekkoTransportOrderRepository(system)
  val handlingUnitRepository = PekkoHandlingUnitRepository(system)
  val workstationRepository = PekkoWorkstationRepository(system)
  val slotRepository = PekkoSlotRepository(system)
  val inventoryRepository = PekkoInventoryRepository(system)

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
