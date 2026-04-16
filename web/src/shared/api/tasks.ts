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
      { actualQuantity: 8, assignedTo: "u002", createdAt: "2026-04-14T06:50:00Z", destinationLocationId: "l005", id: "t007", orderId: "o013", requestedQuantity: 8, skuId: "s002", sourceLocationId: "l001", state: "Completed", taskType: "Pick", waveId: "w006" },
      { actualQuantity: 15, assignedTo: "u003", createdAt: "2026-04-13T16:35:00Z", destinationLocationId: "l004", id: "t008", orderId: "o014", requestedQuantity: 15, skuId: "s001", sourceLocationId: "l002", state: "Completed", taskType: "Pick", waveId: "w007" },
      { actualQuantity: 6, assignedTo: "u004", createdAt: "2026-04-13T16:40:00Z", destinationLocationId: "l005", id: "t009", orderId: "o015", requestedQuantity: 6, skuId: "s003", sourceLocationId: "l001", state: "Completed", taskType: "Pick", waveId: "w007" },
      { actualQuantity: null, assignedTo: "u002", createdAt: "2026-04-14T10:20:00Z", destinationLocationId: "l004", id: "t010", orderId: "o011", requestedQuantity: 4, skuId: "s005", sourceLocationId: "l002", state: "Assigned", taskType: "Pick", waveId: "w005" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T10:22:00Z", destinationLocationId: "l004", id: "t011", orderId: "o012", requestedQuantity: 7, skuId: "s001", sourceLocationId: "l001", state: "Allocated", taskType: "Pick", waveId: "w005" },
      { actualQuantity: 20, assignedTo: "u003", createdAt: "2026-04-13T11:25:00Z", destinationLocationId: "l001", id: "t012", orderId: "o018", requestedQuantity: 20, skuId: "s001", sourceLocationId: "l003", state: "Completed", taskType: "Putaway", waveId: "w009" },
      { actualQuantity: 12, assignedTo: "u004", createdAt: "2026-04-13T11:30:00Z", destinationLocationId: "l002", id: "t013", orderId: "o019", requestedQuantity: 12, skuId: "s002", sourceLocationId: "l003", state: "Completed", taskType: "Putaway", waveId: "w009" },
      { actualQuantity: null, assignedTo: "u002", createdAt: "2026-04-14T11:05:00Z", destinationLocationId: "l005", id: "t014", orderId: "o017", requestedQuantity: 3, skuId: "s004", sourceLocationId: "l001", state: "Assigned", taskType: "Pick", waveId: "w008" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T12:05:00Z", destinationLocationId: null, id: "t015", orderId: "o023", requestedQuantity: 9, skuId: "s003", sourceLocationId: null, state: "Planned", taskType: "Pick", waveId: "w010" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T09:30:00Z", destinationLocationId: "l002", id: "t016", orderId: "o001", requestedQuantity: 30, skuId: "s002", sourceLocationId: "l003", state: "Allocated", taskType: "Replenish", waveId: null },
      { actualQuantity: 5, assignedTo: "u003", createdAt: "2026-04-13T09:05:00Z", destinationLocationId: "l005", id: "t017", orderId: "o025", requestedQuantity: 5, skuId: "s005", sourceLocationId: "l001", state: "Completed", taskType: "Pick", waveId: "w011" },
      { actualQuantity: null, assignedTo: null, createdAt: "2026-04-14T13:35:00Z", destinationLocationId: "l001", id: "t018", orderId: "o026", requestedQuantity: 25, skuId: "s001", sourceLocationId: "l003", state: "Allocated", taskType: "Transfer", waveId: null },
      { actualQuantity: 4, assignedTo: "u004", createdAt: "2026-04-13T14:10:00Z", destinationLocationId: null, id: "t019", orderId: "o008", requestedQuantity: 4, skuId: "s003", sourceLocationId: null, state: "Cancelled", taskType: "Pick", waveId: "w004" },
      { actualQuantity: 7, assignedTo: "u002", createdAt: "2026-04-14T07:00:00Z", destinationLocationId: "l002", id: "t020", orderId: "o013", requestedQuantity: 7, skuId: "s004", sourceLocationId: "l003", state: "Completed", taskType: "Putaway", waveId: "w006" },
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
