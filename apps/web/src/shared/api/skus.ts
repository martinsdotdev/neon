import { unwrapForQuery } from "@neon/client/query"
import { queryOptions } from "@tanstack/react-query"
import { apiClient } from "./client"
import type { Sku } from "@neon/domain/sku"

const MOCK_SKUS: Array<Sku> = import.meta.env.DEV
  ? [
      {
        code: "SKU-10001",
        description: "Widget A",
        id: "s001",
        lotManaged: false,
      },
      {
        code: "SKU-10002",
        description: "Widget B (lot-managed)",
        id: "s002",
        lotManaged: true,
      },
      {
        code: "SKU-20001",
        description: "Gadget X",
        id: "s003",
        lotManaged: false,
      },
      {
        code: "SKU-20002",
        description: "Gadget Y",
        id: "s004",
        lotManaged: true,
      },
      {
        code: "SKU-30001",
        description: "Component Alpha",
        id: "s005",
        lotManaged: false,
      },
    ]
  : []

export const skuQueries = {
  all: () =>
    queryOptions({
      queryFn: async () =>
        unwrapForQuery(apiClient.get<Array<Sku>>("/api/skus"), {
          fallback: import.meta.env.DEV ? MOCK_SKUS : undefined,
        }),
      queryKey: ["skus"] as const,
    }),
  detail: (id: string) =>
    queryOptions({
      enabled: !!id,
      queryFn: async () =>
        unwrapForQuery(apiClient.get<Sku>(`/api/skus/${id}`), {
          fallback: import.meta.env.DEV
            ? (MOCK_SKUS.find((s) => s.id === id) ?? null)
            : undefined,
        }),
      queryKey: ["skus", id] as const,
    }),
}
