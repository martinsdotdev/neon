import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"

export interface Sku {
  code: string
  description: string
  id: string
  lotManaged: boolean
}

const MOCK_SKUS: Sku[] = import.meta.env.DEV
  ? [
      { code: "SKU-10001", description: "Widget A", id: "s001", lotManaged: false },
      { code: "SKU-10002", description: "Widget B (lot-managed)", id: "s002", lotManaged: true },
      { code: "SKU-20001", description: "Gadget X", id: "s003", lotManaged: false },
      { code: "SKU-20002", description: "Gadget Y", id: "s004", lotManaged: true },
      { code: "SKU-30001", description: "Component Alpha", id: "s005", lotManaged: false },
    ]
  : []

export const skuQueries = {
  all: () =>
    queryOptions({
      queryFn: async () => {
        const result = await apiClient.get<Sku[]>("/api/skus")
        return result.unwrapOr(MOCK_SKUS)
      },
      queryKey: ["skus"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () => {
        const result = await apiClient.get<Sku>(`/api/skus/${id}`)
        return result.unwrapOr(MOCK_SKUS.find((s) => s.id === id) ?? null)
      },
      queryKey: ["skus", id] as const,
    }),
}
