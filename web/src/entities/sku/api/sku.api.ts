import type { Sku } from "@/shared/types/sku"
import { mockSkus } from "./sku.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getSkus(): Promise<Sku[]> {
  await delay(200)
  return mockSkus
}

export async function getSku(
  id: string,
): Promise<Sku | undefined> {
  await delay(200)
  return mockSkus.find((s) => s.id === id)
}
