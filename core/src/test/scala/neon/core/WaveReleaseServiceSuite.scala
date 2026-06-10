package neon.core

import neon.common.{
  AllocationStrategy,
  LotAttributes,
  OrderId,
  PackagingLevel,
  SkuId,
  WarehouseAreaId
}
import neon.consolidationgroup.ConsolidationGroupRepository
import neon.order.{Order, OrderLine}
import neon.stockposition.{StockPosition, StockPositionRepository}
import neon.task.TaskRepository
import neon.wave.{OrderGrouping, Wave, WavePlanner, WaveRepository}
import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec

import java.time.{Instant, LocalDate}

class WaveReleaseServiceSuite extends AnyFunSpec with OptionValues:
  val skuId = SkuId()
  val orderId = OrderId()
  val warehouseAreaId = WarehouseAreaId()
  val at = Instant.now()

  def singleOrder(
      id: OrderId = orderId,
      skuId: SkuId = skuId,
      quantity: Int = 10
  ): Order =
    Order(id, neon.common.Priority.Normal, List(OrderLine(skuId, PackagingLevel.Each, quantity)))

  def multiLineOrder(id: OrderId = orderId): Order =
    Order(
      id,
      neon.common.Priority.Normal,
      List(
        OrderLine(skuId = SkuId(), packagingLevel = PackagingLevel.Each, quantity = 5),
        OrderLine(skuId = SkuId(), packagingLevel = PackagingLevel.Case, quantity = 3)
      )
    )

  def stockPosition(
      skuId: SkuId = skuId,
      availableQuantity: Int = 100
  ): StockPosition =
    val (sp, _) =
      StockPosition.create(
        skuId = skuId,
        warehouseAreaId = warehouseAreaId,
        lotAttributes = LotAttributes(),
        onHandQuantity = availableQuantity,
        at = at
      )
    sp

  def buildService(
      waveRepository: WaveRepository = InMemoryWaveRepository(),
      taskRepository: TaskRepository = InMemoryTaskRepository(),
      consolidationGroupRepository: ConsolidationGroupRepository =
        InMemoryConsolidationGroupRepository(),
      stockPositionRepository: Option[StockPositionRepository] = None,
      allocationStrategy: AllocationStrategy = AllocationStrategy.Fifo,
      referenceDate: LocalDate = LocalDate.now()
  ): WaveReleaseService =
    WaveReleaseService(
      waveRepository = waveRepository,
      taskRepository = taskRepository,
      consolidationGroupRepository = consolidationGroupRepository,
      stockPositionRepository = stockPositionRepository,
      allocationStrategy = allocationStrategy,
      referenceDate = referenceDate
    )

  describe("WaveReleaseService"):
    describe("wave persistence"):
      it("saves wave in Released state"):
        val waveRepository = InMemoryWaveRepository()
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService(waveRepository = waveRepository)
        service.release(wavePlan, at)
        assert(waveRepository.store(wavePlan.wave.id).isInstanceOf[Wave.Released])
        assert(waveRepository.events.size == 1)

    describe("task creation"):
      it("creates tasks from task requests via TaskCreationPolicy"):
        val taskRepository = InMemoryTaskRepository()
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService(taskRepository = taskRepository)
        val result = service.release(wavePlan, at)
        assert(result.tasks.size == 1)
        val (planned, event) = result.tasks.head
        assert(planned.skuId == skuId)
        assert(planned.requestedQuantity == 10)
        assert(planned.waveId.value == wavePlan.wave.id)

      it("creates one task per order line for multi-line orders"):
        val taskRepository = InMemoryTaskRepository()
        val order = multiLineOrder()
        val wavePlan = WavePlanner.plan(List(order), OrderGrouping.Single, at)
        val service = buildService(taskRepository = taskRepository)
        val result = service.release(wavePlan, at)
        assert(result.tasks.size == 2)
        assert(taskRepository.store.size == 2)

      it("persists all created tasks"):
        val taskRepository = InMemoryTaskRepository()
        val order1 = singleOrder(id = OrderId())
        val order2 = singleOrder(id = OrderId())
        val wavePlan = WavePlanner.plan(List(order1, order2), OrderGrouping.Single, at)
        val service = buildService(taskRepository = taskRepository)
        service.release(wavePlan, at)
        assert(taskRepository.store.size == 2)
        assert(taskRepository.events.size == 2)

    describe("consolidation group formation"):
      it("creates consolidation groups for Multi grouping"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val order1 = singleOrder(id = OrderId())
        val order2 = singleOrder(id = OrderId())
        val wavePlan = WavePlanner.plan(List(order1, order2), OrderGrouping.Multi, at)
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        val result = service.release(wavePlan, at)
        assert(result.consolidationGroups.size == 1)
        val (created, event) = result.consolidationGroups.head
        assert(created.orderIds.toSet == Set(order1.id, order2.id))
        assert(consolidationGroupRepository.store.size == 1)

      it("skips consolidation groups for Single grouping"):
        val consolidationGroupRepository = InMemoryConsolidationGroupRepository()
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service =
          buildService(consolidationGroupRepository = consolidationGroupRepository)
        val result = service.release(wavePlan, at)
        assert(result.consolidationGroups.isEmpty)
        assert(consolidationGroupRepository.store.isEmpty)

    describe("stock allocation"):
      it("locks stock positions when stock repository is provided"):
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = stockPosition(skuId = skuId, availableQuantity = 100)
        stockPositionRepository.store(sp.id) = sp
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService(
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.release(wavePlan, at, warehouseAreaId = Some(warehouseAreaId))
        assert(result.stockAllocations.nonEmpty)
        val updatedPosition = stockPositionRepository.store(sp.id)
        assert(updatedPosition.allocatedQuantity == 10)
        assert(updatedPosition.availableQuantity == 90)

      it("sets stockPositionId on created tasks"):
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp = stockPosition(skuId = skuId, availableQuantity = 100)
        stockPositionRepository.store(sp.id) = sp
        val taskRepository = InMemoryTaskRepository()
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService(
          taskRepository = taskRepository,
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.release(wavePlan, at, warehouseAreaId = Some(warehouseAreaId))
        val (planned, _) = result.tasks.head
        assert(planned.stockPositionId.contains(sp.id))
        assert(taskRepository.store(planned.id).stockPositionId.contains(sp.id))

      it("skips allocation when stock repository is not provided"):
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService(stockPositionRepository = None)
        val result = service.release(wavePlan, at)
        assert(result.stockAllocations.isEmpty)
        val (planned, _) = result.tasks.head
        assert(planned.stockPositionId.isEmpty)

      it("allocates across multiple stock positions for a single task"):
        val stockPositionRepository = InMemoryStockPositionRepository()
        val sp1 = stockPosition(skuId = skuId, availableQuantity = 4)
        val sp2 = stockPosition(skuId = skuId, availableQuantity = 10)
        stockPositionRepository.store(sp1.id) = sp1
        stockPositionRepository.store(sp2.id) = sp2
        val wavePlan =
          WavePlanner.plan(List(singleOrder(quantity = 10)), OrderGrouping.Single, at)
        val service = buildService(
          stockPositionRepository = Some(stockPositionRepository)
        )
        val result =
          service.release(wavePlan, at, warehouseAreaId = Some(warehouseAreaId))
        assert(result.stockAllocations.size == 1)
        val allocationResult = result.stockAllocations.head
        assert(allocationResult.allocations.size == 2)
        assert(allocationResult.shortQuantity == 0)

    describe("result"):
      it("carries wave, event, tasks, and consolidation groups"):
        val wavePlan = WavePlanner.plan(List(singleOrder()), OrderGrouping.Single, at)
        val service = buildService()
        val result = service.release(wavePlan, at)
        assert(result.wave == wavePlan.wave)
        assert(result.event == wavePlan.event)
        assert(result.tasks.nonEmpty)
        assert(result.consolidationGroups.isEmpty)
        assert(result.stockAllocations.isEmpty)
