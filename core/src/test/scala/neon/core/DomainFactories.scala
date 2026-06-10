package neon.core

import neon.common.{
  ConsolidationGroupId,
  HandlingUnitId,
  LocationId,
  LotAttributes,
  OrderId,
  PackagingLevel,
  SkuId,
  StockPositionId,
  TaskId,
  TransportOrderId,
  UserId,
  WarehouseAreaId,
  WaveId
}
import neon.consolidationgroup.ConsolidationGroup
import neon.stockposition.StockPosition
import neon.task.{Task, TaskType}
import neon.transportorder.TransportOrder
import neon.wave.{OrderGrouping, Wave}

import java.time.Instant

/** Shared identifiers and aggregate factories for core service suites. Mix into a suite to build
  * domain objects with sensible defaults instead of hand-rolling them per suite. Identifiers are
  * fresh per suite instance; factories default to the suite-level identifiers so related objects
  * (task, wave, consolidation group) agree with each other out of the box.
  */
trait DomainFactories:
  val skuId: SkuId = SkuId()
  val userId: UserId = UserId()
  val orderId: OrderId = OrderId()
  val waveId: WaveId = WaveId()
  val handlingUnitId: HandlingUnitId = HandlingUnitId()
  val sourceLocationId: LocationId = LocationId()
  val destinationLocationId: LocationId = LocationId()
  val warehouseAreaId: WarehouseAreaId = WarehouseAreaId()
  val at: Instant = Instant.now()

  def plannedWave(id: WaveId = waveId): Wave.Planned =
    Wave.Planned(id, OrderGrouping.Single, List(orderId))

  def releasedWave(id: WaveId = waveId): Wave.Released =
    Wave.Released(id, OrderGrouping.Single, List(orderId))

  def completedWave(id: WaveId = waveId): Wave.Completed =
    Wave.Completed(id, OrderGrouping.Single)

  def cancelledWave(id: WaveId = waveId): Wave.Cancelled =
    Wave.Cancelled(id, OrderGrouping.Single)

  def plannedTask(
      id: TaskId = TaskId(),
      requestedQuantity: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Planned =
    Task.Planned(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = requestedQuantity,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = handlingUnitId
    )

  def allocatedTask(
      id: TaskId = TaskId(),
      requestedQuantity: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId),
      stockPositionId: Option[StockPositionId] = None
  ): Task.Allocated =
    Task.Allocated(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = requestedQuantity,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = handlingUnitId,
      stockPositionId = stockPositionId,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId
    )

  def assignedTask(
      id: TaskId = TaskId(),
      requestedQuantity: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId),
      stockPositionId: Option[StockPositionId] = None
  ): Task.Assigned =
    Task.Assigned(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = requestedQuantity,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = handlingUnitId,
      stockPositionId = stockPositionId,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId,
      assignedTo = userId
    )

  def completedTask(
      id: TaskId = TaskId(),
      requestedQuantity: Int = 10,
      actualQuantity: Int = 10,
      orderId: OrderId = orderId,
      waveId: Option[WaveId] = Some(waveId),
      handlingUnitId: Option[HandlingUnitId] = Some(handlingUnitId)
  ): Task.Completed =
    Task.Completed(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      requestedQuantity = requestedQuantity,
      actualQuantity = actualQuantity,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = handlingUnitId,
      stockPositionId = None,
      sourceLocationId = sourceLocationId,
      destinationLocationId = destinationLocationId,
      assignedTo = userId
    )

  def cancelledTask(
      id: TaskId = TaskId(),
      waveId: Option[WaveId] = Some(waveId)
  ): Task.Cancelled =
    Task.Cancelled(
      id = id,
      taskType = TaskType.Pick,
      skuId = skuId,
      packagingLevel = PackagingLevel.Each,
      orderId = orderId,
      waveId = waveId,
      parentTaskId = None,
      handlingUnitId = None,
      stockPositionId = None,
      sourceLocationId = None,
      destinationLocationId = None,
      assignedTo = None
    )

  def pendingTransportOrder(
      handlingUnitId: HandlingUnitId = handlingUnitId,
      destination: LocationId = destinationLocationId
  ): TransportOrder.Pending =
    TransportOrder.Pending(TransportOrderId(), handlingUnitId, destination)

  def confirmedTransportOrder(
      handlingUnitId: HandlingUnitId = handlingUnitId,
      destination: LocationId = destinationLocationId
  ): TransportOrder.Confirmed =
    TransportOrder.Confirmed(TransportOrderId(), handlingUnitId, destination)

  def createdConsolidationGroup(
      waveId: WaveId = waveId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.Created =
    ConsolidationGroup.Created(ConsolidationGroupId(), waveId, orderIds)

  def pickedConsolidationGroup(
      waveId: WaveId = waveId,
      orderIds: List[OrderId] = List(orderId)
  ): ConsolidationGroup.Picked =
    ConsolidationGroup.Picked(ConsolidationGroupId(), waveId, orderIds)

  def allocatedStockPosition(
      allocatedQuantity: Int = 10,
      availableQuantity: Int = 90
  ): StockPosition =
    val (stockPosition, _) = StockPosition.create(
      skuId = skuId,
      warehouseAreaId = warehouseAreaId,
      lotAttributes = LotAttributes(),
      onHandQuantity = allocatedQuantity + availableQuantity,
      at = at
    )
    val (allocated, _) = stockPosition.allocate(allocatedQuantity, at)
    allocated
