import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

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
  state: "Planned" | "Allocated" | "Assigned" | "Completed" | "Cancelled"
  taskType: "Pick" | "Putaway" | "Replenish" | "Transfer"
  waveId: string | null
}

const MOCK_TASKS: Task[] = import.meta.env.DEV
  ? [
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T08:05:00Z", destinationLocationId: "l004", id: "t001", orderId: "o001", requestedQuantity: 5, skuId: "s001", sourceLocationId: "l001", state: "Allocated", taskType: "Pick", waveId: "w001" },
      { actualQuantity: null, assignedTo: "u003", createdAt: "2026-04-14T08:06:00Z", destinationLocationId: "l004", id: "t002", orderId: "o001", requestedQuantity: 2, skuId: "s003", sourceLocationId: "l002", state: "Assigned", taskType: "Pick", waveId: "w001" },
      { actualQuantity: 10, assignedTo: "u003", createdAt: "2026-04-14T07:35:00Z", destinationLocationId: "l005", id: "t003", orderId: "o004", requestedQuantity: 10, skuId: "s002", sourceLocationId: "l001", state: "Completed", taskType: "Pick", waveId: "w002" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T09:01:00Z", destinationLocationId: null, id: "t004", orderId: "o005", requestedQuantity: 3, skuId: "s001", sourceLocationId: null, state: "Planned", taskType: "Pick", waveId: "w003" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T09:10:00Z", destinationLocationId: "l001", id: "t005", orderId: "o001", requestedQuantity: 20, skuId: "s001", sourceLocationId: "l003", state: "Planned", taskType: "Replenish", waveId: null },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-13T14:05:00Z", destinationLocationId: null, id: "t006", orderId: "o007", requestedQuantity: 4, skuId: "s004", sourceLocationId: null, state: "Cancelled", taskType: "Pick", waveId: "w004" },
    ]
  : []

export const taskQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Task[]>("/api/tasks")
        return result.unwrapOr(MOCK_TASKS)
      },
      queryKey: ["tasks"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Task>(`/api/tasks/${id}`)
        return result.unwrapOr(MOCK_TASKS.find((t) => t.id === id) ?? null)
      },
      queryKey: ["tasks", id] as const,
    }),
}
