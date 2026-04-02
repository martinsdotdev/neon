import type {
  HandlingUnitId,
  LocationId,
  OrderId,
  SkuId,
  TaskId,
  UserId,
  WaveId,
} from "./ids"
import type { PackagingLevel, TaskStatus, TaskType } from "./enums"

export interface Task {
  id: TaskId
  status: TaskStatus
  taskType: TaskType
  skuId: SkuId
  packagingLevel: PackagingLevel
  requestedQuantity: number
  actualQuantity: number | null
  orderId: OrderId
  waveId: WaveId | null
  parentTaskId: TaskId | null
  handlingUnitId: HandlingUnitId | null
  sourceLocationId: LocationId | null
  destinationLocationId: LocationId | null
  assignedTo: UserId | null
  createdAt: string
  updatedAt: string
}
