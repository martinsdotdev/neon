import { z } from "zod"

export type TaskState =
  | "Planned"
  | "Allocated"
  | "Assigned"
  | "Completed"
  | "Cancelled"

export type TaskPriority = "high" | "medium" | "low"

export type PackagingLevel = "Each" | "Inner" | "Case" | "Pallet"

export type TaskType = "Pick" | "Putaway" | "Replenish" | "Transfer"

export interface TaskTimelineEvent {
  state: TaskState
  at: string
  by: string
  note?: string
}

export interface Task {
  actualQuantity: number | null
  assignedTo: string | null
  createdAt: string
  destinationLocationId: string | null
  id: string
  orderId: string
  requestedQuantity: number
  skuId: string
  sourceLocationId: string | null
  state: TaskState
  taskType: TaskType
  waveId: string | null
  // Optional fields populated by mock fixtures and projection joins.
  priority?: TaskPriority
  skuName?: string
  packagingLevel?: PackagingLevel
  handlingUnitId?: string | null
  timeline?: Array<TaskTimelineEvent>
}

export const ALL_TASK_STATES: ReadonlyArray<TaskState> = [
  "Planned",
  "Allocated",
  "Assigned",
  "Completed",
  "Cancelled",
]

// Happy-path states (no Cancelled) for stepper / timeline rendering.
export const STATE_ORDER: ReadonlyArray<TaskState> = [
  "Planned",
  "Allocated",
  "Assigned",
  "Completed",
]

export const STATE_LABEL: Record<TaskState, string> = {
  Allocated: "Allocated",
  Assigned: "Assigned",
  Cancelled: "Cancelled",
  Completed: "Completed",
  Planned: "Planned",
}

// Legal state transitions mirror the Scala typestate (task/Task.scala).
export const LEGAL_TRANSITIONS: Record<TaskState, ReadonlyArray<TaskState>> = {
  Allocated: ["Assigned", "Cancelled"],
  Assigned: ["Completed", "Cancelled"],
  Cancelled: [],
  Completed: [],
  Planned: ["Allocated", "Cancelled"],
}

export const TaskStateSchema = z.enum([
  "Planned",
  "Allocated",
  "Assigned",
  "Completed",
  "Cancelled",
])

export const PackagingLevelSchema = z.enum(["Each", "Inner", "Case", "Pallet"])

export const TaskTypeSchema = z.enum([
  "Pick",
  "Putaway",
  "Replenish",
  "Transfer",
])

export const TaskSchema = z.object({
  actualQuantity: z.number().int().nullable(),
  assignedTo: z.string().uuid().nullable(),
  createdAt: z.string().datetime(),
  destinationLocationId: z.string().uuid().nullable(),
  handlingUnitId: z.string().uuid().nullable().optional(),
  id: z.string().uuid(),
  orderId: z.string(),
  packagingLevel: PackagingLevelSchema.optional(),
  priority: z.enum(["high", "medium", "low"]).optional(),
  requestedQuantity: z.number().int().nonnegative(),
  skuId: z.string(),
  skuName: z.string().optional(),
  sourceLocationId: z.string().uuid().nullable(),
  state: TaskStateSchema,
  taskType: TaskTypeSchema,
  waveId: z.string().uuid().nullable(),
}) satisfies z.ZodType<Task>

export const CompleteTaskInputSchema = z.object({
  actualQuantity: z.number().int().nonnegative(),
  verified: z.boolean(),
})

export type CompleteTaskInput = z.infer<typeof CompleteTaskInputSchema>
