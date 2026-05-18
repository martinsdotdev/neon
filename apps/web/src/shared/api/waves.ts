import { queryOptions } from "@tanstack/react-query"
import { ResultAsync, errAsync } from "neverthrow"
import { apiClient } from "./client"

export interface Wave {
  createdAt: string
  id: string
  orderCount: number
  orderGrouping: "Single" | "Multi"
  orderIds: Array<string>
  state: "Planned" | "Released" | "Completed" | "Cancelled"
}

const MOCK_WAVES: Array<Wave> = import.meta.env.DEV
  ? [
      {
        createdAt: "2026-04-14T08:00:00Z",
        id: "w001",
        orderCount: 3,
        orderGrouping: "Multi",
        orderIds: ["o001", "o002", "o003"],
        state: "Released",
      },
      {
        createdAt: "2026-04-14T07:30:00Z",
        id: "w002",
        orderCount: 1,
        orderGrouping: "Single",
        orderIds: ["o004"],
        state: "Completed",
      },
      {
        createdAt: "2026-04-14T09:00:00Z",
        id: "w003",
        orderCount: 2,
        orderGrouping: "Multi",
        orderIds: ["o005", "o006"],
        state: "Planned",
      },
      {
        createdAt: "2026-04-13T14:00:00Z",
        id: "w004",
        orderCount: 4,
        orderGrouping: "Multi",
        orderIds: ["o007", "o008", "o009", "o010"],
        state: "Cancelled",
      },
      {
        createdAt: "2026-04-14T10:15:00Z",
        id: "w005",
        orderCount: 2,
        orderGrouping: "Multi",
        orderIds: ["o011", "o012"],
        state: "Released",
      },
      {
        createdAt: "2026-04-14T06:45:00Z",
        id: "w006",
        orderCount: 1,
        orderGrouping: "Single",
        orderIds: ["o013"],
        state: "Completed",
      },
      {
        createdAt: "2026-04-13T16:30:00Z",
        id: "w007",
        orderCount: 3,
        orderGrouping: "Multi",
        orderIds: ["o014", "o015", "o016"],
        state: "Completed",
      },
      {
        createdAt: "2026-04-14T11:00:00Z",
        id: "w008",
        orderCount: 1,
        orderGrouping: "Single",
        orderIds: ["o017"],
        state: "Released",
      },
      {
        createdAt: "2026-04-13T11:20:00Z",
        id: "w009",
        orderCount: 5,
        orderGrouping: "Multi",
        orderIds: ["o018", "o019", "o020", "o021", "o022"],
        state: "Completed",
      },
      {
        createdAt: "2026-04-14T12:00:00Z",
        id: "w010",
        orderCount: 2,
        orderGrouping: "Multi",
        orderIds: ["o023", "o024"],
        state: "Planned",
      },
      {
        createdAt: "2026-04-13T09:00:00Z",
        id: "w011",
        orderCount: 1,
        orderGrouping: "Single",
        orderIds: ["o025"],
        state: "Completed",
      },
      {
        createdAt: "2026-04-14T13:30:00Z",
        id: "w012",
        orderCount: 3,
        orderGrouping: "Multi",
        orderIds: ["o026", "o027", "o028"],
        state: "Released",
      },
    ]
  : []

export const waveQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Array<Wave>>("/api/waves")
        return result.unwrapOr(MOCK_WAVES)
      },
      queryKey: ["waves"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Wave>(`/api/waves/${id}`)
        return result.unwrapOr(MOCK_WAVES.find((w) => w.id === id) ?? null)
      },
      queryKey: ["waves", id] as const,
    }),
}

// ---------------------------------------------------------------------------
// Mutations — kept in shared/api per FSD ("CRUD belongs in shared, not
// entities"). Surface returns ResultAsync<T, ApiError> so callers can
// `.match(onOk, onErr)` and drive toast / navigation without throwing.
// ---------------------------------------------------------------------------

export interface PlanAndReleaseRequest {
  orderIds: Array<string>
  grouping: Wave["orderGrouping"]
  dockAssignments: Array<{ dockId: string; carrierId: string }>
}

export interface WaveReleaseResponse {
  consolidationGroupsCreated: number
  status: string
  tasksCreated: number
  waveId: string
}

// Generate a synthetic wave ID for the dev mock. Math.random is fine here —
// no SSR exposure (mutations only fire on user click, never during render).
const mockWaveId = () => `w_${Math.random().toString(36).slice(2, 8)}`

// Synthesize a plan-and-release response that mirrors what the backend
// would return. Used only when the real fetch errors out in dev (no
// running backend) so the wizard's success-redirect flow still works.
const mockPlanResponse = (
  body: PlanAndReleaseRequest
): WaveReleaseResponse => ({
  consolidationGroupsCreated:
    body.grouping === "Single" ? 1 : body.orderIds.length,
  status: "Released",
  tasksCreated: body.orderIds.length * 3,
  waveId: mockWaveId(),
})

export const waveMutations = {
  planAndRelease: (body: PlanAndReleaseRequest) =>
    apiClient
      .post<WaveReleaseResponse>("/api/waves/plan-and-release", body)
      .orElse((error) => {
        // Only fall back in dev on a network error — production must surface
        // real errors to the user instead of pretending they succeeded.
        if (!import.meta.env.DEV || error.kind !== "network") {
          return errAsync(error)
        }
        return ResultAsync.fromSafePromise(
          Promise.resolve(mockPlanResponse(body))
        )
      }),
}
