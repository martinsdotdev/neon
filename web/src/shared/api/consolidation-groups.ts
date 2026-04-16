import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface ConsolidationGroup {
  createdAt: string
  id: string
  orderCount: number
  orderIds: Array<string>
  state:
    | "Created"
    | "Picked"
    | "ReadyForWorkstation"
    | "Assigned"
    | "Completed"
    | "Cancelled"
  waveId: string
  workstationId: string | null
}

const MOCK_CGS: Array<ConsolidationGroup> = import.meta.env.DEV
  ? [
      {
        createdAt: "2026-04-14T08:10:00Z",
        id: "cg001",
        orderCount: 3,
        orderIds: ["o001", "o002", "o003"],
        state: "Assigned",
        waveId: "w001",
        workstationId: "ws001",
      },
      {
        createdAt: "2026-04-14T07:40:00Z",
        id: "cg002",
        orderCount: 1,
        orderIds: ["o004"],
        state: "Completed",
        waveId: "w002",
        workstationId: "ws002",
      },
      {
        createdAt: "2026-04-14T09:05:00Z",
        id: "cg003",
        orderCount: 2,
        orderIds: ["o005", "o006"],
        state: "Created",
        waveId: "w003",
        workstationId: null,
      },
      {
        createdAt: "2026-04-13T16:35:00Z",
        id: "cg004",
        orderCount: 3,
        orderIds: ["o014", "o015", "o016"],
        state: "Completed",
        waveId: "w007",
        workstationId: "ws001",
      },
      {
        createdAt: "2026-04-14T10:18:00Z",
        id: "cg005",
        orderCount: 2,
        orderIds: ["o011", "o012"],
        state: "Picked",
        waveId: "w005",
        workstationId: null,
      },
      {
        createdAt: "2026-04-13T11:22:00Z",
        id: "cg006",
        orderCount: 5,
        orderIds: ["o018", "o019", "o020", "o021", "o022"],
        state: "Completed",
        waveId: "w009",
        workstationId: "ws002",
      },
      {
        createdAt: "2026-04-14T11:02:00Z",
        id: "cg007",
        orderCount: 1,
        orderIds: ["o017"],
        state: "ReadyForWorkstation",
        waveId: "w008",
        workstationId: null,
      },
      {
        createdAt: "2026-04-13T14:02:00Z",
        id: "cg008",
        orderCount: 4,
        orderIds: ["o007", "o008", "o009", "o010"],
        state: "Cancelled",
        waveId: "w004",
        workstationId: null,
      },
    ]
  : []

export const consolidationGroupQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Array<ConsolidationGroup>>(
          "/api/consolidation-groups"
        )
        return result.unwrapOr(MOCK_CGS)
      },
      queryKey: ["consolidation-groups"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<ConsolidationGroup>(
          `/api/consolidation-groups/${id}`
        )
        return result.unwrapOr(MOCK_CGS.find((cg) => cg.id === id) ?? null)
      },
      queryKey: ["consolidation-groups", id] as const,
    }),
}
