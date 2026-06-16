# Appendix D: API Reference

All endpoints use JSON request and response bodies with circe marshalling. Authentication is cookie-based (session token). Each endpoint requires a specific permission, enforced by `AuthDirectives.requirePermission`.

## Error Responses (RFC 9457 Problem Details)

Every non-2xx response is an RFC 9457 Problem Details document with the `application/problem+json` content type, not a bare status code with a text body (ADR-0011). The body is produced by `ProblemMapper`, which holds one `given` instance per domain error ADT, so the error-to-status mapping lives at a single seam.

A problem response has this shape:

```json
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "urn:neon:error:task-not-found",
  "status": 404,
  "title": "Task not found",
  "detail": "Task 0190b6e0-... was not found"
}
```

| Field      | Type   | Description                                                                                |
| ---------- | ------ | ----------------------------------------------------------------------------------------- |
| `type`     | URI    | Identifies the error kind. Neon uses `urn:neon:error:<slug>`; `about:blank` for generic.  |
| `status`   | number | HTTP status code, mirrored from the response line.                                        |
| `title`    | string | Short, human-readable summary of the error kind.                                          |
| `detail`   | string | Explanation specific to this occurrence (optional; omitted when absent).                  |
| `instance` | URI    | Identifies this specific occurrence (optional; omitted when absent).                      |

Null fields are dropped from the JSON. In the tables below, the **Errors** column lists each error as `<status> <ErrorName>`; the `type` slug is the kebab-case form of the error name (for example `TaskNotFound` becomes `urn:neon:error:task-not-found`). The full slug-and-status catalogue appears in the [Error Type Reference](#error-type-reference) at the end of this appendix.

---

## Authentication

Base path: `/auth`

| Method | Path           | Permission      | Request Body          | Response Body                                  | Errors                                      |
| ------ | -------------- | --------------- | --------------------- | ---------------------------------------------- | ------------------------------------------- |
| POST   | `/auth/login`  | (none)          | `{ login, password }` | `{ userId, login, name, role, permissions[] }` | 401 InvalidCredentials, 403 AccountInactive |
| POST   | `/auth/logout` | (none)          | (none)                | 200 OK                                         | (none)                                      |
| GET    | `/auth/me`     | (authenticated) | (none)                | `{ userId, login, name, role, permissions[] }` | 401 Unauthorized                            |

Login sets an HttpOnly session cookie (`session`). Logout clears it.

---

## Waves

Base path: `/waves`

| Method | Path                      | Permission    | Request Body                                                       | Response Body                                                                                | Errors                                      |
| ------ | ------------------------- | ------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- | ------------------------------------------- |
| POST   | `/waves/plan-and-release` | `wave:plan`   | `{ orderIds[], grouping, dockAssignments[{ dockId, carrierId }] }` | `{ status, waveId, tasksCreated, consolidationGroupsCreated }`                               | 409 DockConflict, 422 other planning errors |
| DELETE | `/waves/{waveId}`         | `wave:cancel` | (none)                                                             | `{ status, waveId, cancelledTasks, cancelledTransportOrders, cancelledConsolidationGroups }` | 404 WaveNotFound, 409 WaveAlreadyTerminal   |

---

## Tasks

Base path: `/tasks`

| Method | Path                       | Permission      | Request Body                                  | Response Body                                                                            | Errors                                                                                           |
| ------ | -------------------------- | --------------- | --------------------------------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| POST   | `/tasks/{taskId}/complete` | `task:complete` | `{ actualQuantity, verified }`                | `{ status, taskId, actualQuantity, requestedQuantity, hasShortpick, hasTransportOrder }` | 404 TaskNotFound, 409 TaskNotAssigned, 422 InvalidActualQuantity, 428 VerificationRequired       |
| POST   | `/tasks/{taskId}/allocate` | `task:allocate` | `{ sourceLocationId, destinationLocationId }` | `{ status, taskId, sourceLocationId, destinationLocationId }`                            | 404 TaskNotFound, 409 TaskInWrongState or TaskAlreadyTerminal, 422 UserNotFound or UserNotActive |
| POST   | `/tasks/{taskId}/assign`   | `task:assign`   | `{ userId }`                                  | `{ status, taskId, userId }`                                                             | 404 TaskNotFound, 409 TaskInWrongState or TaskAlreadyTerminal, 422 UserNotFound or UserNotActive |
| DELETE | `/tasks/{taskId}`          | `task:cancel`   | (none)                                        | `{ status, taskId }`                                                                     | 404 TaskNotFound, 409 TaskInWrongState or TaskAlreadyTerminal                                    |

---

## Consolidation Groups

Base path: `/consolidation-groups`

| Method | Path                                                | Permission                     | Request Body | Response Body                                           | Errors                                                                                                             |
| ------ | --------------------------------------------------- | ------------------------------ | ------------ | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| POST   | `/consolidation-groups/{id}/complete`               | `consolidation-group:complete` | (none)       | `{ status, consolidationGroupId, workstationReleased }` | 404 ConsolidationGroupNotFound, 409 ConsolidationGroupNotAssigned or WorkstationNotActive, 422 WorkstationNotFound |
| POST   | `/consolidation-groups/{id}/ready-for-workstation`  | `consolidation-group:advance`  | (none)       | `{ status, consolidationGroupId }`                      | 404 ConsolidationGroupNotFound, 409 ConsolidationGroupNotPicked                                                    |
| DELETE | `/consolidation-groups/{id}`                        | `consolidation-group:cancel`   | (none)       | `{ status, consolidationGroupId }`                      | 404 ConsolidationGroupNotFound, 409 ConsolidationGroupAlreadyTerminal                                              |

---

## Handling Units

Base path: `/handling-units`

All handling unit endpoints require the `handling-unit:manage` permission.

| Method | Path                                 | Permission             | Request Body | Response Body                | Errors                                                 |
| ------ | ------------------------------------ | ---------------------- | ------------ | ---------------------------- | ------------------------------------------------------ |
| POST   | `/handling-units/{id}/pack`          | `handling-unit:manage` | (none)       | `{ status, handlingUnitId }` | 404 HandlingUnitNotFound, 409 HandlingUnitInWrongState |
| POST   | `/handling-units/{id}/ready-to-ship` | `handling-unit:manage` | (none)       | `{ status, handlingUnitId }` | 404 HandlingUnitNotFound, 409 HandlingUnitInWrongState |
| POST   | `/handling-units/{id}/ship`          | `handling-unit:manage` | (none)       | `{ status, handlingUnitId }` | 404 HandlingUnitNotFound, 409 HandlingUnitInWrongState |
| POST   | `/handling-units/{id}/empty`         | `handling-unit:manage` | (none)       | `{ status, handlingUnitId }` | 404 HandlingUnitNotFound, 409 HandlingUnitInWrongState |

---

## Transport Orders

Base path: `/transport-orders`

| Method | Path                             | Permission                | Request Body | Response Body                                                          | Errors                                                                                                           |
| ------ | -------------------------------- | ------------------------- | ------------ | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| POST   | `/transport-orders/{id}/confirm` | `transport-order:confirm` | (none)       | `{ status, transportOrderId, handlingUnitInBuffer, bufferCompletion }` | 404 TransportOrderNotFound, 409 TransportOrderNotPending or HandlingUnitNotPickCreated, 422 HandlingUnitNotFound |
| DELETE | `/transport-orders/{id}`         | `transport-order:cancel`  | (none)       | `{ status, transportOrderId }`                                         | 404 TransportOrderNotFound, 409 TransportOrderAlreadyTerminal                                                    |

---

## Workstations

Base path: `/workstations`

| Method | Path                         | Permission           | Request Body                     | Response Body                                           | Errors                                                                                     |
| ------ | ---------------------------- | -------------------- | -------------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| POST   | `/workstations`              | `workstation:manage` | `{ workstationType, slotCount }` | `{ status, workstationId, workstationType, slotCount }` | 422 creation error                                                                         |
| POST   | `/workstations/{id}/enable`  | `workstation:manage` | (none)                           | `{ status, workstationId }`                             | 404 WorkstationNotFound, 409 WorkstationInWrongState                                       |
| POST   | `/workstations/{id}/disable` | `workstation:manage` | (none)                           | `{ status, workstationId }`                             | 404 WorkstationNotFound, 409 WorkstationInWrongState                                       |
| POST   | `/workstations/assign`       | `workstation:assign` | `{ consolidationGroupId }`       | `{ status, consolidationGroupId, workstationId }`       | 404 ConsolidationGroupNotFound, 409 ConsolidationGroupNotReady, 503 NoWorkstationAvailable |

---

## Slots

Base path: `/slots`

All slot endpoints require the `slot:manage` permission.

| Method | Path                   | Permission    | Request Body                  | Response Body        | Errors                                 |
| ------ | ---------------------- | ------------- | ----------------------------- | -------------------- | -------------------------------------- |
| POST   | `/slots/{id}/reserve`  | `slot:manage` | `{ orderId, handlingUnitId }` | `{ status, slotId }` | 404 SlotNotFound, 409 SlotInWrongState |
| POST   | `/slots/{id}/complete` | `slot:manage` | (none)                        | `{ status, slotId }` | 404 SlotNotFound, 409 SlotInWrongState |
| POST   | `/slots/{id}/release`  | `slot:manage` | (none)                        | `{ status, slotId }` | 404 SlotNotFound, 409 SlotInWrongState |

---

## Inventory

Base path: `/inventory`

All inventory endpoints require the `inventory:manage` permission.

| Method | Path                          | Permission         | Request Body                                          | Response Body             | Errors                                                                |
| ------ | ----------------------------- | ------------------ | ----------------------------------------------------- | ------------------------- | --------------------------------------------------------------------- |
| POST   | `/inventory`                  | `inventory:manage` | `{ locationId, skuId, packagingLevel, lot?, onHand }` | `{ status, inventoryId }` | 404 InventoryNotFound, 422 InvalidQuantity                            |
| POST   | `/inventory/{id}/reserve`     | `inventory:manage` | `{ quantity }`                                        | `{ status, inventoryId }` | 404 InventoryNotFound, 409 InsufficientAvailable, 422 InvalidQuantity |
| POST   | `/inventory/{id}/release`     | `inventory:manage` | `{ quantity }`                                        | `{ status, inventoryId }` | 404 InventoryNotFound, 409 InsufficientReserved, 422 InvalidQuantity  |
| POST   | `/inventory/{id}/consume`     | `inventory:manage` | `{ quantity }`                                        | `{ status, inventoryId }` | 404 InventoryNotFound, 409 InsufficientReserved, 422 InvalidQuantity  |
| POST   | `/inventory/{id}/correct-lot` | `inventory:manage` | `{ lot? }`                                            | `{ status, inventoryId }` | 404 InventoryNotFound, 409 ReservedNotZero                            |

---

## Stock Positions

Base path: `/stock-positions`

All stock position endpoints require the `stock:manage` permission.

| Method | Path                            | Permission     | Request Body                                       | Response Body                 | Errors                                                                    |
| ------ | ------------------------------- | -------------- | -------------------------------------------------- | ----------------------------- | ------------------------------------------------------------------------- |
| POST   | `/stock-positions`              | `stock:manage` | `{ skuId, warehouseAreaId, lot?, onHandQuantity }` | `{ status, stockPositionId }` | 422 InvalidQuantity                                                       |
| POST   | `/stock-positions/{id}/block`   | `stock:manage` | `{ quantity }`                                     | `{ status, stockPositionId }` | 404 StockPositionNotFound, 409 InsufficientAvailable, 422 InvalidQuantity |
| POST   | `/stock-positions/{id}/unblock` | `stock:manage` | `{ quantity }`                                     | `{ status, stockPositionId }` | 404 StockPositionNotFound, 409 InsufficientBlocked, 422 InvalidQuantity   |
| POST   | `/stock-positions/{id}/adjust`  | `stock:manage` | `{ delta, reasonCode }`                            | `{ status, stockPositionId }` | 404 StockPositionNotFound, 422 InvalidQuantity                            |

---

## Inbound

Base path: `/inbound`

All inbound endpoints require the `inbound:manage` permission.

### Deliveries

| Method | Path                               | Permission       | Request Body                                        | Response Body            | Errors                                         |
| ------ | ---------------------------------- | ---------------- | --------------------------------------------------- | ------------------------ | ---------------------------------------------- |
| POST   | `/inbound/deliveries`              | `inbound:manage` | `{ skuId, packagingLevel, lot?, expectedQuantity }` | `{ status, deliveryId }` | 409 DeliveryInWrongState                       |
| POST   | `/inbound/deliveries/{id}/receive` | `inbound:manage` | `{ quantity, rejectedQuantity }`                    | `{ status, deliveryId }` | 404 DeliveryNotFound, 409 DeliveryInWrongState |

### Receipts

| Method | Path                                 | Permission       | Request Body                                                    | Response Body           | Errors                                       |
| ------ | ------------------------------------ | ---------------- | --------------------------------------------------------------- | ----------------------- | -------------------------------------------- |
| POST   | `/inbound/receipts`                  | `inbound:manage` | `{ inboundDeliveryId }`                                         | `{ status, receiptId }` | 409 ReceiptInWrongState                      |
| POST   | `/inbound/receipts/{id}/record-line` | `inbound:manage` | `{ skuId, quantity, packagingLevel, lot?, targetContainerId? }` | `{ status, receiptId }` | 404 ReceiptNotFound, 409 ReceiptInWrongState |
| POST   | `/inbound/receipts/{id}/confirm`     | `inbound:manage` | (none)                                                          | `{ status, receiptId }` | 404 ReceiptNotFound, 409 ReceiptInWrongState |

---

## Cycle Counts

Base path: `/cycle-counts`

All cycle count endpoints require the `cycle-count:manage` permission.

| Method | Path                                       | Permission           | Request Body                                            | Response Body              | Errors                                             |
| ------ | ------------------------------------------ | -------------------- | ------------------------------------------------------- | -------------------------- | -------------------------------------------------- |
| POST   | `/cycle-counts`                            | `cycle-count:manage` | `{ warehouseAreaId, skuIds[], countType, countMethod }` | `{ status, cycleCountId }` | 409 CycleCountInWrongState                         |
| POST   | `/cycle-counts/{id}/start`                 | `cycle-count:manage` | (none)                                                  | `{ status, cycleCountId }` | 404 CycleCountNotFound, 409 CycleCountInWrongState |
| POST   | `/cycle-counts/{id}/tasks/{taskId}/assign` | `cycle-count:manage` | `{ userId }`                                            | `{ status, countTaskId }`  | 404 CountTaskNotFound, 409 CountTaskInWrongState   |
| POST   | `/cycle-counts/{id}/tasks/{taskId}/record` | `cycle-count:manage` | `{ actualQuantity }`                                    | `{ status, countTaskId }`  | 404 CountTaskNotFound, 409 CountTaskInWrongState   |

---

## Permission Reference

| Permission Key                 | Description                            |
| ------------------------------ | -------------------------------------- |
| `wave:plan`                    | Plan and release waves                 |
| `wave:cancel`                  | Cancel waves                           |
| `task:complete`                | Complete tasks                         |
| `task:allocate`                | Allocate tasks to locations            |
| `task:assign`                  | Assign tasks to users                  |
| `task:cancel`                  | Cancel tasks                           |
| `transport-order:confirm`      | Confirm transport order delivery       |
| `transport-order:cancel`       | Cancel transport orders                |
| `consolidation-group:complete` | Complete consolidation groups          |
| `consolidation-group:advance`  | Mark consolidation groups ready for a workstation |
| `consolidation-group:cancel`   | Cancel consolidation groups            |
| `workstation:assign`           | Assign work to workstations            |
| `workstation:manage`           | Create, enable, disable workstations   |
| `handling-unit:manage`         | Manage handling unit lifecycle         |
| `slot:manage`                  | Reserve, complete, release slots       |
| `inventory:manage`             | Manage inventory positions             |
| `stock:manage`                 | Manage stock positions                 |
| `inbound:manage`               | Manage inbound deliveries and receipts |
| `cycle-count:manage`           | Manage cycle counts and count tasks    |
| `user:manage`                  | Manage user accounts                   |

---

## Error Type Reference

Every domain error maps to a problem `type` of the form `urn:neon:error:<slug>` and a fixed HTTP status, via the `given ProblemMapper` instances in `ProblemMapper.scala`. The same slug is reused across error ADTs where the meaning is identical (for example `consolidation-group-not-found` and `insufficient-available` each appear under more than one operation). The table below lists every slug the API can emit, grouped by status.

| Status | `type` slug (prefixed `urn:neon:error:`)                                                                                                                                                                                                                                                                                                  |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 404    | `task-not-found`, `wave-not-found`, `consolidation-group-not-found`, `handling-unit-not-found`, `slot-not-found`, `transport-order-not-found`, `workstation-not-found`, `inventory-not-found`, `stock-position-not-found`, `cycle-count-not-found`, `count-task-not-found`, `delivery-not-found`, `receipt-not-found`                       |
| 409    | `task-not-assigned`, `task-in-wrong-state`, `task-already-terminal`, `dock-conflict`, `wave-already-terminal`, `consolidation-group-not-assigned`, `consolidation-group-not-picked`, `consolidation-group-not-ready`, `consolidation-group-already-terminal`, `workstation-not-active`, `workstation-in-wrong-state`, `handling-unit-in-wrong-state`, `handling-unit-not-pick-created`, `slot-in-wrong-state`, `transport-order-not-pending`, `transport-order-already-terminal`, `insufficient-available`, `insufficient-reserved`, `insufficient-blocked`, `reserved-not-zero`, `cycle-count-in-wrong-state`, `count-task-in-wrong-state`, `delivery-in-wrong-state`, `receipt-in-wrong-state` |
| 422    | `invalid-actual-quantity`, `user-not-found`, `user-not-active`, `empty-orders`, `no-dock-assignments`, `order-without-carrier`, `carrier-not-found`, `carrier-inactive`, `dock-not-found`, `dock-is-not-shipping-dock`, `duplicate-carrier-in-assignments`, `duplicate-dock-in-assignments`, `carrier-not-mapped-to-any-dock`, `workstation-not-found`, `handling-unit-not-found`, `invalid-quantity`                                                                                       |
| 428    | `verification-required`                                                                                                                                                                                                                                                                                                                   |
| 503    | `no-workstation-available`                                                                                                                                                                                                                                                                                                                |

Note that a few slugs appear under more than one status because distinct error ADTs reuse the name for different conditions: `workstation-not-found` is 404 in the workstation lifecycle but 422 when a consolidation-group completion references a missing workstation, and `handling-unit-not-found` is 404 in the handling-unit lifecycle but 422 when a transport-order confirmation references a missing handling unit.
