import type { Task, TaskState } from "@neon/domain/task"
import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "./client"

// Backend MobileTaskRoutes.TaskView shape. Mirrors @neon/domain Task but
// keeps the optional-vs-required fields explicit per-state.
export interface MobileTask {
  id: string
  taskType: Task["taskType"]
  state: TaskState
  skuId: string
  orderId: string
  waveId: string | null
  handlingUnitId: string | null
  requestedQuantity: number
  actualQuantity: number | null
  sourceLocationId: string | null
  destinationLocationId: string | null
  assignedTo: string | null
}

interface TaskListResponse {
  tasks: Array<MobileTask>
}

export const taskQueries = {
  assigned: (userId: string, state: TaskState | "all" = "Assigned") =>
    queryOptions({
      queryFn: async (): Promise<Array<MobileTask>> => {
        const stateParam = state === "all" ? "" : `&state=${state}`
        const result = await apiClient.get<TaskListResponse>(
          `/tasks?assignedTo=${userId}${stateParam}`,
        )
        if (result.isErr()) throw result.error
        return result.value.tasks
      },
      queryKey: ["tasks", "assigned", userId, state] as const,
    }),

  byId: (taskId: string) =>
    queryOptions({
      queryFn: async (): Promise<MobileTask> => {
        const result = await apiClient.get<MobileTask>(`/tasks/${taskId}`)
        if (result.isErr()) throw result.error
        return result.value
      },
      queryKey: ["tasks", "byId", taskId] as const,
    }),
}

interface CompleteTaskResponse {
  status: string
  taskId: string
  actualQuantity: number
  requestedQuantity: number
  hasShortpick: boolean
  hasTransportOrder: boolean
}

export const useCompleteTask = (taskId: string) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (input: { actualQuantity: number; verified: boolean }) => {
      const result = await apiClient.post<CompleteTaskResponse>(
        `/tasks/${taskId}/complete`,
        input,
      )
      if (result.isErr()) throw result.error
      return result.value
    },
    onSuccess: () => {
      // Invalidate the assigned-tasks list so the completed task moves out
      // and any newly-spawned shortpick replacement appears.
      queryClient.invalidateQueries({ queryKey: ["tasks"] })
    },
  })
}

export const useClaimTask = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (taskId: string) => {
      const result = await apiClient.post<{
        status: string
        taskId: string
        userId: string
      }>(`/tasks/${taskId}/claim`)
      if (result.isErr()) throw result.error
      return result.value
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] })
    },
  })
}
