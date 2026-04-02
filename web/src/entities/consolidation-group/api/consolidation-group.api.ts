import type { ConsolidationGroup } from "@/shared/types/consolidation-group"
import { mockConsolidationGroups } from "./consolidation-group.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getConsolidationGroups(): Promise<
  ConsolidationGroup[]
> {
  await delay(200)
  return mockConsolidationGroups
}

export async function getConsolidationGroup(
  id: string,
): Promise<ConsolidationGroup | undefined> {
  await delay(200)
  return mockConsolidationGroups.find(
    (cg) => cg.id === id,
  )
}
