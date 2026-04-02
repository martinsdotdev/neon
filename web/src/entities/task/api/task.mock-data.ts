import type { Task } from "@/shared/types/task"
import type {
  LocationId,
  OrderId,
  SkuId,
  TaskId,
  UserId,
  WaveId,
} from "@/shared/types/ids"

export const mockTasks: Task[] = [
  {
    id: "0193a5b0-0003-7000-8000-000000000001" as TaskId,
    status: "Planned",
    taskType: "Pick",
    skuId: "0193a5b0-0004-7000-8000-000000000001" as SkuId,
    packagingLevel: "Each",
    requestedQuantity: 10,
    actualQuantity: null,
    orderId:
      "0193a5b0-0002-7000-8000-000000000001" as OrderId,
    waveId:
      "0193a5b0-0001-7000-8000-000000000001" as WaveId,
    parentTaskId: null,
    handlingUnitId: null,
    sourceLocationId: null,
    destinationLocationId: null,
    assignedTo: null,
    createdAt: "2026-04-01T08:00:00Z",
    updatedAt: "2026-04-01T08:00:00Z",
  },
  {
    id: "0193a5b0-0003-7000-8000-000000000002" as TaskId,
    status: "Allocated",
    taskType: "Putaway",
    skuId: "0193a5b0-0004-7000-8000-000000000002" as SkuId,
    packagingLevel: "Case",
    requestedQuantity: 5,
    actualQuantity: null,
    orderId:
      "0193a5b0-0002-7000-8000-000000000002" as OrderId,
    waveId:
      "0193a5b0-0001-7000-8000-000000000001" as WaveId,
    parentTaskId: null,
    handlingUnitId: null,
    sourceLocationId:
      "0193a5b0-0005-7000-8000-000000000001" as LocationId,
    destinationLocationId:
      "0193a5b0-0005-7000-8000-000000000002" as LocationId,
    assignedTo: null,
    createdAt: "2026-04-01T07:30:00Z",
    updatedAt: "2026-04-01T08:10:00Z",
  },
  {
    id: "0193a5b0-0003-7000-8000-000000000003" as TaskId,
    status: "Assigned",
    taskType: "Pick",
    skuId: "0193a5b0-0004-7000-8000-000000000003" as SkuId,
    packagingLevel: "Each",
    requestedQuantity: 8,
    actualQuantity: null,
    orderId:
      "0193a5b0-0002-7000-8000-000000000003" as OrderId,
    waveId:
      "0193a5b0-0001-7000-8000-000000000002" as WaveId,
    parentTaskId: null,
    handlingUnitId: null,
    sourceLocationId:
      "0193a5b0-0005-7000-8000-000000000003" as LocationId,
    destinationLocationId:
      "0193a5b0-0005-7000-8000-000000000004" as LocationId,
    assignedTo:
      "0193a5b0-0006-7000-8000-000000000001" as UserId,
    createdAt: "2026-04-01T07:00:00Z",
    updatedAt: "2026-04-01T08:20:00Z",
  },
  {
    id: "0193a5b0-0003-7000-8000-000000000004" as TaskId,
    status: "Completed",
    taskType: "Pick",
    skuId: "0193a5b0-0004-7000-8000-000000000004" as SkuId,
    packagingLevel: "InnerPack",
    requestedQuantity: 12,
    actualQuantity: 10,
    orderId:
      "0193a5b0-0002-7000-8000-000000000004" as OrderId,
    waveId:
      "0193a5b0-0001-7000-8000-000000000003" as WaveId,
    parentTaskId: null,
    handlingUnitId: null,
    sourceLocationId:
      "0193a5b0-0005-7000-8000-000000000005" as LocationId,
    destinationLocationId:
      "0193a5b0-0005-7000-8000-000000000006" as LocationId,
    assignedTo:
      "0193a5b0-0006-7000-8000-000000000002" as UserId,
    createdAt: "2026-04-01T06:00:00Z",
    updatedAt: "2026-04-01T09:00:00Z",
  },
  {
    id: "0193a5b0-0003-7000-8000-000000000005" as TaskId,
    status: "Cancelled",
    taskType: "Replenish",
    skuId: "0193a5b0-0004-7000-8000-000000000005" as SkuId,
    packagingLevel: "Pallet",
    requestedQuantity: 20,
    actualQuantity: null,
    orderId:
      "0193a5b0-0002-7000-8000-000000000006" as OrderId,
    waveId:
      "0193a5b0-0001-7000-8000-000000000004" as WaveId,
    parentTaskId: null,
    handlingUnitId: null,
    sourceLocationId: null,
    destinationLocationId: null,
    assignedTo: null,
    createdAt: "2026-03-31T14:00:00Z",
    updatedAt: "2026-03-31T14:30:00Z",
  },
]
