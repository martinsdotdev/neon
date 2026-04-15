# Appendix A: State Machine Diagrams

Every domain aggregate in Neon WES is listed below with its state machine. Aggregates that use the typestate pattern encode states as case classes inside a sealed trait hierarchy; transition methods exist only on valid source states.

---

## Wave

A wave groups orders for batch processing through the warehouse. Planned waves are released to trigger task creation, then complete when all tasks finish.

```mermaid
stateDiagram-v2
    [*] --> Planned
    Planned --> Released : release(at)
    Planned --> Cancelled : cancel(at)
    Released --> Completed : complete(at)
    Released --> Cancelled : cancel(at)
    Completed --> [*]
    Cancelled --> [*]
```

---

## Task

A task represents a single atomic warehouse operation (pick, putaway, replenish, or transfer) for one SKU. Shortpick handling occurs at the Assigned-to-Completed transition when `actualQuantity < requestedQuantity`.

```mermaid
stateDiagram-v2
    [*] --> Planned : create(...)
    Planned --> Allocated : allocate(src, dest, at)
    Planned --> Cancelled : cancel(at)
    Allocated --> Assigned : assign(userId, at)
    Allocated --> Cancelled : cancel(at)
    Assigned --> Completed : complete(actualQty, at)
    Assigned --> Cancelled : cancel(at)
    Completed --> [*]
    Cancelled --> [*]
```

---

## ConsolidationGroup

A consolidation group batches orders from a wave for workstation processing. It progresses through picking, buffer arrival, workstation assignment, and completion.

```mermaid
stateDiagram-v2
    [*] --> Created : create(waveId, orderIds, at)
    Created --> Picked : pick(at)
    Created --> Cancelled : cancel(at)
    Picked --> ReadyForWorkstation : readyForWorkstation(at)
    Picked --> Cancelled : cancel(at)
    ReadyForWorkstation --> Assigned : assign(workstationId, at)
    ReadyForWorkstation --> Cancelled : cancel(at)
    Assigned --> Completed : complete(at)
    Assigned --> Cancelled : cancel(at)
    Completed --> [*]
    Cancelled --> [*]
```

---

## HandlingUnit

A handling unit is a physical container that moves through the warehouse. Two independent lifecycle streams share the same sealed trait.

### Pick Stream

Pick handling units transport picked items from storage to a consolidation buffer, then are emptied after deconsolidation.

```mermaid
stateDiagram-v2
    [*] --> PickCreated
    PickCreated --> InBuffer : moveToBuffer(locationId, at)
    InBuffer --> Empty : empty(at)
    Empty --> [*]
```

### Ship Stream

Ship handling units carry packed orders from workstations through outbound shipping.

```mermaid
stateDiagram-v2
    [*] --> ShipCreated
    ShipCreated --> Packed : pack(at)
    Packed --> ReadyToShip : readyToShip(at)
    ReadyToShip --> Shipped : ship(at)
    Shipped --> [*]
```

---

## TransportOrder

A transport order routes a handling unit to a destination. It represents the temporal gap between task completion and operator confirmation at the destination.

```mermaid
stateDiagram-v2
    [*] --> Pending : create(huId, dest, at)
    Pending --> Confirmed : confirm(at)
    Pending --> Cancelled : cancel(at)
    Confirmed --> [*]
    Cancelled --> [*]
```

---

## Workstation

A workstation is a physical station (put-wall or pack station) where consolidation and packing operations occur. It cycles between Idle and Active and can be disabled from either state.

```mermaid
stateDiagram-v2
    [*] --> Disabled
    Disabled --> Idle : enable(at)
    Idle --> Active : assign(assignmentId, at)
    Idle --> Idle : switchMode(newMode, at)
    Idle --> Disabled : disable(at)
    Active --> Idle : release(at)
    Active --> Disabled : disable(at)
```

---

## Slot

A slot is a put-wall position within a workstation. Each slot is bound to one order at a time.

```mermaid
stateDiagram-v2
    [*] --> Available
    Available --> Reserved : reserve(orderId, huId, at)
    Reserved --> Completed : complete(at)
    Reserved --> Available : release(at)
    Completed --> [*]
```

---

## InboundDelivery

An inbound delivery represents an expected receipt of goods into the warehouse. It tracks expected, received, and rejected quantities through the receiving process.

```mermaid
stateDiagram-v2
    [*] --> New
    New --> Receiving : startReceiving(at)
    New --> Cancelled : cancel(at)
    Receiving --> Receiving : receive(qty, rejected, at)
    Receiving --> Received : complete(at)
    Receiving --> Closed : close(at)
    Received --> [*]
    Closed --> [*]
    Cancelled --> [*]
```

---

## GoodsReceipt

A goods receipt represents a physical receiving session against an inbound delivery. Lines are recorded incrementally before confirmation.

```mermaid
stateDiagram-v2
    [*] --> Open
    Open --> Open : recordLine(line, at)
    Open --> Confirmed : confirm(at)
    Open --> Cancelled : cancel(at)
    Confirmed --> [*]
    Cancelled --> [*]
```

---

## CycleCount

A cycle count represents a scheduled or ad-hoc inventory verification for a set of SKUs within a warehouse area. It groups individual count tasks.

```mermaid
stateDiagram-v2
    [*] --> New
    New --> InProgress : start(at)
    New --> Cancelled : cancel(at)
    InProgress --> Completed : complete(at)
    InProgress --> Cancelled : cancel(at)
    Completed --> [*]
    Cancelled --> [*]
```

---

## CountTask

A count task represents a single SKU-location count within a cycle count. The counter records the actual quantity; variance is computed automatically.

```mermaid
stateDiagram-v2
    [*] --> Pending
    Pending --> Assigned : assign(userId, at)
    Pending --> Cancelled : cancel(at)
    Assigned --> Recorded : record(actualQty, at)
    Assigned --> Cancelled : cancel(at)
    Recorded --> [*]
    Cancelled --> [*]
```

---

## StockPosition (4-Bucket Model)

A stock position is an area-level inventory position keyed by (SKU, warehouse area, lot attributes). It does not follow a state machine; instead, it maintains a 4-bucket quantity model with an enforced invariant.

```mermaid
stateDiagram-v2
    state "StockPosition" as sp {
        state "onHandQuantity" as oh
        state "availableQuantity" as avail
        state "allocatedQuantity" as alloc
        state "reservedQuantity" as res
        state "blockedQuantity" as blk
    }
    note right of sp
        Invariant: onHand == available + allocated + reserved + blocked
        All quantities >= 0
    end note
```

**Bucket transitions:**

| Operation            | From                   | To                 | Trigger                             |
| -------------------- | ---------------------- | ------------------ | ----------------------------------- |
| `allocate`           | available              | allocated          | Outbound order allocation           |
| `deallocate`         | allocated              | available          | Task cancellation                   |
| `consumeAllocated`   | allocated (and onHand) | removed            | Task completion                     |
| `addQuantity`        | (external)             | onHand + available | Inbound receiving                   |
| `reserve`            | available              | reserved           | Internal ops (counting, relocation) |
| `releaseReservation` | reserved               | available          | Internal ops complete               |
| `block`              | available              | blocked            | Administrative hold                 |
| `unblock`            | blocked                | available          | Hold released                       |
| `adjust`             | onHand + available     | adjusted           | SOX-compliant correction            |

---

## HandlingUnitStock (4-Bucket Model)

A handling unit stock is a container-level inventory position keyed by (container, slot, stock position). It mirrors the StockPosition 4-bucket model at the physical container level.

```mermaid
stateDiagram-v2
    state "HandlingUnitStock" as hus {
        state "onHandQuantity" as oh2
        state "availableQuantity" as avail2
        state "allocatedQuantity" as alloc2
        state "reservedQuantity" as res2
        state "blockedQuantity" as blk2
    }
    note right of hus
        Invariant: onHand == available + allocated + reserved + blocked
        All quantities >= 0
    end note
```

The operations are identical to StockPosition: `allocate`, `deallocate`, `addQuantity`, `consumeAllocated`, `reserve`, `releaseReservation`, `block`, `unblock`, `adjust`, and `changeStatus`.

---

## Inventory (2-Bucket Model)

An inventory position is identified by the (location, SKU, lot) triad and tracks on-hand and reserved quantities. Available is a computed value, not a stored bucket.

```mermaid
stateDiagram-v2
    state "Inventory" as inv {
        state "onHand" as oh3
        state "reserved" as res3
        state "available = onHand - reserved" as avail3
    }
```

| Operation    | Effect                         | Trigger                                 |
| ------------ | ------------------------------ | --------------------------------------- |
| `reserve`    | reserved += qty                | Outbound allocation                     |
| `release`    | reserved -= qty                | Task cancellation                       |
| `consume`    | onHand -= qty, reserved -= qty | Task completion                         |
| `correctLot` | lot updated                    | Lot correction (requires reserved == 0) |
