package neon.core

import neon.common.{AllocationStrategy, SkuId, WarehouseAreaId}
import neon.consolidationgroup.{
  ConsolidationGroup,
  ConsolidationGroupEvent,
  ConsolidationGroupRepository
}
import neon.stockposition.{StockPosition, StockPositionRepository}
import neon.task.{Task, TaskEvent, TaskRepository}
import neon.wave.{Wave, WaveEvent, WavePlan, WaveRepository}

import java.time.{Instant, LocalDate}

/** The result of a successful wave release, containing the released wave, the created tasks, any
  * consolidation groups formed, and stock allocation results.
  *
  * @param wave
  *   the released wave
  * @param event
  *   the wave released event
  * @param tasks
  *   planned tasks created from the wave's task requests
  * @param consolidationGroups
  *   consolidation groups formed from the wave's order grouping, empty if none apply
  * @param stockAllocations
  *   stock allocation results, empty if stock allocation was not performed
  */
case class WaveReleaseResult(
    wave: Wave.Released,
    event: WaveEvent.WaveReleased,
    tasks: List[(Task.Planned, TaskEvent.TaskCreated)],
    consolidationGroups: List[
      (ConsolidationGroup.Created, ConsolidationGroupEvent.ConsolidationGroupCreated)
    ],
    stockAllocations: List[AllocationResult] = Nil
)

/** Releases a wave plan, persisting the wave, creating tasks from its task requests, forming
  * consolidation groups from its order grouping, and optionally allocating stock.
  *
  * @param waveRepository
  *   repository for wave persistence
  * @param taskRepository
  *   repository for task persistence
  * @param consolidationGroupRepository
  *   repository for consolidation group persistence
  * @param stockPositionRepository
  *   optional repository for stock position persistence; when None, allocation is skipped
  * @param allocationStrategy
  *   strategy for sorting stock positions during allocation
  * @param referenceDate
  *   date for shelf life computation during allocation
  */
class WaveReleaseService(
    waveRepository: WaveRepository,
    taskRepository: TaskRepository,
    consolidationGroupRepository: ConsolidationGroupRepository,
    stockPositionRepository: Option[StockPositionRepository] = None,
    allocationStrategy: AllocationStrategy = AllocationStrategy.Fifo,
    referenceDate: LocalDate = LocalDate.now()
):
  /** Releases a [[WavePlan]], creating tasks and consolidation groups, and optionally allocating
    * stock.
    *
    * Steps: (1) persist the released wave, (2) create planned tasks from task requests via
    * [[TaskCreationPolicy]], (3) if a stock position repository is provided, allocate stock via
    * [[StockAllocationPolicy]], lock stock positions, and set stockPositionId on tasks, (4) form
    * consolidation groups via [[ConsolidationGroupFormationPolicy]].
    *
    * @param wavePlan
    *   the wave plan containing the released wave and its task requests
    * @param at
    *   instant of the release
    * @param warehouseAreaId
    *   the warehouse area to allocate stock from; required when stockPositionRepository is provided
    * @return
    *   the release result with all created entities
    */
  def release(
      wavePlan: WavePlan,
      at: Instant,
      warehouseAreaId: Option[WarehouseAreaId] = None
  ): WaveReleaseResult =
    waveRepository.save(wavePlan.wave, wavePlan.event)

    val baseTasks = TaskCreationPolicy(wavePlan.taskRequests, at)

    val (tasks, stockAllocations) = (stockPositionRepository, warehouseAreaId) match
      case (Some(spRepo), Some(areaId)) =>
        allocateAndEnrich(spRepo, areaId, baseTasks, at)
      case _ => (baseTasks, Nil)

    taskRepository.saveAll(tasks)

    val consolidationGroups = ConsolidationGroupFormationPolicy(wavePlan.event, at)
    consolidationGroupRepository.saveAll(consolidationGroups)

    WaveReleaseResult(
      wave = wavePlan.wave,
      event = wavePlan.event,
      tasks = tasks,
      consolidationGroups = consolidationGroups,
      stockAllocations = stockAllocations
    )

  private def allocateAndEnrich(
      spRepo: StockPositionRepository,
      warehouseAreaId: WarehouseAreaId,
      baseTasks: List[(Task.Planned, TaskEvent.TaskCreated)],
      at: Instant
  ): (List[(Task.Planned, TaskEvent.TaskCreated)], List[AllocationResult]) =
    val allSkuIds = baseTasks.map(_._1.skuId).distinct

    val availableStock: Map[SkuId, List[StockPosition]] = allSkuIds.map { skuId =>
      skuId -> spRepo.findBySkuAndArea(skuId, warehouseAreaId)
    }.toMap

    val requests = baseTasks.map { (planned, _) =>
      AllocationRequest(planned.skuId, planned.requestedQuantity)
    }

    StockAllocationPolicy(requests, availableStock, allocationStrategy, referenceDate) match
      case Left(_) =>
        // Allocation failed; proceed without stock allocation
        (baseTasks, Nil)
      case Right(allocationResults) =>
        // Lock stock positions using the in-memory map (avoid redundant findById calls)
        val positionsById = availableStock.values.flatten.map(sp => sp.id -> sp).toMap
        val mutablePositions =
          scala.collection.mutable.Map.from(positionsById)
        allocationResults.foreach { result =>
          result.allocations.foreach { allocation =>
            mutablePositions.get(allocation.stockPositionId).foreach { sp =>
              val (updated, event) = sp.allocate(allocation.quantity, at)
              spRepo.save(updated, event)
              mutablePositions(allocation.stockPositionId) = updated
            }
          }
        }

        // Enrich tasks with stockPositionId from their allocation.
        // Each allocation result corresponds to one task (same order).
        val enrichedTasks =
          baseTasks.zip(allocationResults).map { case ((planned, event), allocationResult) =>
            // Use the first allocation's stock position id for the task.
            // When a task draws from multiple positions, we use the primary
            // (first and largest) allocation.
            val stockPositionId =
              allocationResult.allocations.headOption.map(_.stockPositionId)
            val enrichedPlanned = planned.copy(stockPositionId = stockPositionId)
            val enrichedEvent = event.copy(stockPositionId = stockPositionId)
            (enrichedPlanned, enrichedEvent)
          }

        (enrichedTasks, allocationResults)
