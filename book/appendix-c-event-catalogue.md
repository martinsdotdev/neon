# Appendix C: Event Catalogue

Every domain event in Neon WES is listed below, organized by module. All events extend `CborSerializable` for Jackson CBOR serialization. Each event records an `occurredAt: Instant` timestamp.

---

## Wave Events

Sealed trait: `WaveEvent`

Common fields on all wave events: `waveId: WaveId`, `orderGrouping: OrderGrouping`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `WaveReleased` | `orderIds: List[OrderId]` | `Planned.release(at)` |
| `WaveCompleted` | (none) | `Released.complete(at)` |
| `WaveCancelled` | (none) | `Planned.cancel(at)` or `Released.cancel(at)` |

---

## Task Events

Sealed trait: `TaskEvent`

Common fields on all task events: `taskId: TaskId`, `taskType: TaskType`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `TaskCreated` | `skuId: SkuId`, `packagingLevel: PackagingLevel`, `orderId: OrderId`, `waveId: Option[WaveId]`, `parentTaskId: Option[TaskId]`, `handlingUnitId: Option[HandlingUnitId]`, `requestedQuantity: Int`, `stockPositionId: Option[StockPositionId]` | `Task.create(...)` |
| `TaskAllocated` | `sourceLocationId: LocationId`, `destinationLocationId: LocationId` | `Planned.allocate(src, dest, at)` |
| `TaskAssigned` | `userId: UserId` | `Allocated.assign(userId, at)` |
| `TaskCompleted` | `skuId: SkuId`, `packagingLevel: PackagingLevel`, `waveId: Option[WaveId]`, `parentTaskId: Option[TaskId]`, `handlingUnitId: Option[HandlingUnitId]`, `sourceLocationId: LocationId`, `destinationLocationId: LocationId`, `requestedQuantity: Int`, `actualQuantity: Int`, `assignedTo: UserId` | `Assigned.complete(actualQty, at)` |
| `TaskCancelled` | `waveId: Option[WaveId]`, `parentTaskId: Option[TaskId]`, `handlingUnitId: Option[HandlingUnitId]`, `sourceLocationId: Option[LocationId]`, `destinationLocationId: Option[LocationId]`, `assignedTo: Option[UserId]` | `Planned.cancel(at)`, `Allocated.cancel(at)`, or `Assigned.cancel(at)` |

---

## ConsolidationGroup Events

Sealed trait: `ConsolidationGroupEvent`

Common fields: `consolidationGroupId: ConsolidationGroupId`, `waveId: WaveId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `ConsolidationGroupCreated` | `orderIds: List[OrderId]` | `ConsolidationGroup.create(waveId, orderIds, at)` |
| `ConsolidationGroupPicked` | (none) | `Created.pick(at)` |
| `ConsolidationGroupReadyForWorkstation` | (none) | `Picked.readyForWorkstation(at)` |
| `ConsolidationGroupAssigned` | `workstationId: WorkstationId` | `ReadyForWorkstation.assign(wsId, at)` |
| `ConsolidationGroupCompleted` | `workstationId: WorkstationId` | `Assigned.complete(at)` |
| `ConsolidationGroupCancelled` | (none) | `cancel(at)` from any non-terminal state |

---

## HandlingUnit Events

Sealed trait: `HandlingUnitEvent`

Common fields: `handlingUnitId: HandlingUnitId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `HandlingUnitMovedToBuffer` | `locationId: LocationId` | `PickCreated.moveToBuffer(locationId, at)` |
| `HandlingUnitEmptied` | (none) | `InBuffer.empty(at)` |
| `HandlingUnitPacked` | `orderId: OrderId` | `ShipCreated.pack(at)` |
| `HandlingUnitReadyToShip` | `orderId: OrderId` | `Packed.readyToShip(at)` |
| `HandlingUnitShipped` | `orderId: OrderId` | `ReadyToShip.ship(at)` |

---

## TransportOrder Events

Sealed trait: `TransportOrderEvent`

Common fields: `transportOrderId: TransportOrderId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `TransportOrderCreated` | `handlingUnitId: HandlingUnitId`, `destination: LocationId` | `TransportOrder.create(huId, dest, at)` |
| `TransportOrderConfirmed` | `handlingUnitId: HandlingUnitId`, `destination: LocationId` | `Pending.confirm(at)` |
| `TransportOrderCancelled` | `handlingUnitId: HandlingUnitId`, `destination: LocationId` | `Pending.cancel(at)` |

---

## Workstation Events

Sealed trait: `WorkstationEvent`

Common fields: `workstationId: WorkstationId`, `workstationType: WorkstationType`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `WorkstationEnabled` | `slotCount: Int`, `mode: WorkstationMode` | `Disabled.enable(at)` |
| `WorkstationAssigned` | `mode: WorkstationMode`, `assignmentId: UUID` | `Idle.assign(assignmentId, at)` |
| `ModeSwitched` | `previousMode: WorkstationMode`, `newMode: WorkstationMode` | `Idle.switchMode(newMode, at)` |
| `WorkstationReleased` | (none) | `Active.release(at)` |
| `WorkstationDisabled` | (none) | `Idle.disable(at)` or `Active.disable(at)` |

---

## Slot Events

Sealed trait: `SlotEvent`

Common fields: `slotId: SlotId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `SlotReserved` | `workstationId: WorkstationId`, `orderId: OrderId`, `handlingUnitId: HandlingUnitId` | `Available.reserve(orderId, huId, at)` |
| `SlotCompleted` | `workstationId: WorkstationId`, `orderId: OrderId`, `handlingUnitId: HandlingUnitId` | `Reserved.complete(at)` |
| `SlotReleased` | `workstationId: WorkstationId`, `orderId: OrderId` | `Reserved.release(at)` |

---

## Inventory Events

Sealed trait: `InventoryEvent`

Common fields: `inventoryId: InventoryId`, `locationId: LocationId`, `skuId: SkuId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `InventoryCreated` | `packagingLevel: PackagingLevel`, `lot: Option[Lot]`, `onHand: Int` | `Inventory.create(...)` |
| `InventoryReserved` | `lot: Option[Lot]`, `quantityReserved: Int` | `reserve(qty, at)` |
| `InventoryReleased` | `lot: Option[Lot]`, `quantityReleased: Int` | `release(qty, at)` |
| `InventoryConsumed` | `lot: Option[Lot]`, `quantityConsumed: Int` | `consume(qty, at)` |
| `LotCorrected` | `previousLot: Option[Lot]`, `newLot: Option[Lot]` | `correctLot(newLot, at)` |

---

## StockPosition Events

Sealed trait: `StockPositionEvent`

| Event | Fields | Produced By |
|---|---|---|
| `Created` | `stockPositionId: StockPositionId`, `skuId: SkuId`, `warehouseAreaId: WarehouseAreaId`, `lotAttributes: LotAttributes`, `onHandQuantity: Int`, `occurredAt: Instant` | `StockPosition.create(...)` |
| `Allocated` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `allocate(qty, at)` |
| `Deallocated` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `deallocate(qty, at)` |
| `QuantityAdded` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `addQuantity(qty, at)` |
| `AllocatedConsumed` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `consumeAllocated(qty, at)` |
| `Reserved` | `stockPositionId: StockPositionId`, `quantity: Int`, `lockType: StockLockType`, `occurredAt: Instant` | `reserve(qty, lockType, at)` |
| `ReservationReleased` | `stockPositionId: StockPositionId`, `quantity: Int`, `lockType: StockLockType`, `occurredAt: Instant` | `releaseReservation(qty, lockType, at)` |
| `Blocked` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `block(qty, at)` |
| `Unblocked` | `stockPositionId: StockPositionId`, `quantity: Int`, `occurredAt: Instant` | `unblock(qty, at)` |
| `Adjusted` | `stockPositionId: StockPositionId`, `delta: Int`, `reasonCode: AdjustmentReasonCode`, `occurredAt: Instant` | `adjust(delta, reasonCode, at)` |
| `StatusChanged` | `stockPositionId: StockPositionId`, `previousStatus: InventoryStatus`, `newStatus: InventoryStatus`, `occurredAt: Instant` | `changeStatus(newStatus, at)` |

---

## HandlingUnitStock Events

Sealed trait: `HandlingUnitStockEvent`

| Event | Fields | Produced By |
|---|---|---|
| `Created` | `handlingUnitStockId: HandlingUnitStockId`, `skuId: SkuId`, `containerId: ContainerId`, `slotCode: SlotCode`, `stockPositionId: StockPositionId`, `physicalContainer: Boolean`, `onHandQuantity: Int`, `occurredAt: Instant` | `HandlingUnitStock.create(...)` |
| `Allocated` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `allocate(qty, at)` |
| `Deallocated` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `deallocate(qty, at)` |
| `QuantityAdded` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `addQuantity(qty, at)` |
| `AllocatedConsumed` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `consumeAllocated(qty, at)` |
| `Reserved` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `lockType: StockLockType`, `occurredAt: Instant` | `reserve(qty, lockType, at)` |
| `ReservationReleased` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `lockType: StockLockType`, `occurredAt: Instant` | `releaseReservation(qty, lockType, at)` |
| `Blocked` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `block(qty, at)` |
| `Unblocked` | `handlingUnitStockId: HandlingUnitStockId`, `quantity: Int`, `occurredAt: Instant` | `unblock(qty, at)` |
| `Adjusted` | `handlingUnitStockId: HandlingUnitStockId`, `delta: Int`, `reasonCode: AdjustmentReasonCode`, `occurredAt: Instant` | `adjust(delta, reasonCode, at)` |
| `StatusChanged` | `handlingUnitStockId: HandlingUnitStockId`, `previousStatus: InventoryStatus`, `newStatus: InventoryStatus`, `occurredAt: Instant` | `changeStatus(newStatus, at)` |

---

## InboundDelivery Events

Sealed trait: `InboundDeliveryEvent`

Common fields: `inboundDeliveryId: InboundDeliveryId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `InboundDeliveryCreated` | `skuId: SkuId`, `packagingLevel: PackagingLevel`, `lotAttributes: LotAttributes`, `expectedQuantity: Int` | (factory-level creation) |
| `ReceivingStarted` | (none) | `New.startReceiving(at)` |
| `QuantityReceived` | `quantity: Int`, `rejectedQuantity: Int` | `Receiving.receive(qty, rejected, at)` |
| `InboundDeliveryReceived` | `receivedQuantity: Int` | `Receiving.complete(at)` |
| `InboundDeliveryClosed` | `receivedQuantity: Int`, `rejectedQuantity: Int` | `Receiving.close(at)` |
| `InboundDeliveryCancelled` | (none) | `New.cancel(at)` |

---

## GoodsReceipt Events

Sealed trait: `GoodsReceiptEvent`

Common fields: `goodsReceiptId: GoodsReceiptId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `GoodsReceiptCreated` | `inboundDeliveryId: InboundDeliveryId` | (factory-level creation) |
| `LineRecorded` | `line: ReceivedLine` | `Open.recordLine(line, at)` |
| `GoodsReceiptConfirmed` | `inboundDeliveryId: InboundDeliveryId`, `receivedLines: List[ReceivedLine]` | `Open.confirm(at)` |
| `GoodsReceiptCancelled` | (none) | `Open.cancel(at)` |

`ReceivedLine` contains: `skuId: SkuId`, `quantity: Int`, `packagingLevel: PackagingLevel`, `lotAttributes: LotAttributes`, `targetContainerId: Option[ContainerId]`.

---

## CycleCount Events

Sealed trait: `CycleCountEvent`

Common fields: `cycleCountId: CycleCountId`, `countType: CountType`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `CycleCountCreated` | `warehouseAreaId: WarehouseAreaId`, `skuIds: List[SkuId]`, `countMethod: CountMethod` | (factory-level creation) |
| `CycleCountStarted` | `countMethod: CountMethod` | `New.start(at)` |
| `CycleCountCompleted` | (none) | `InProgress.complete(at)` |
| `CycleCountCancelled` | (none) | `New.cancel(at)` or `InProgress.cancel(at)` |

---

## CountTask Events

Sealed trait: `CountTaskEvent`

Common fields: `countTaskId: CountTaskId`, `occurredAt: Instant`.

| Event | Additional Fields | Produced By |
|---|---|---|
| `CountTaskCreated` | `cycleCountId: CycleCountId`, `skuId: SkuId`, `locationId: LocationId`, `expectedQuantity: Int` | (factory-level creation) |
| `CountTaskAssigned` | `userId: UserId` | `Pending.assign(userId, at)` |
| `CountTaskRecorded` | `cycleCountId: CycleCountId`, `skuId: SkuId`, `locationId: LocationId`, `expectedQuantity: Int`, `actualQuantity: Int`, `variance: Int`, `countedBy: UserId` | `Assigned.record(actualQty, at)` |
| `CountTaskCancelled` | `cycleCountId: CycleCountId` | `Pending.cancel(at)` or `Assigned.cancel(at)` |
