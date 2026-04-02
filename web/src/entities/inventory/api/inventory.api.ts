import type { Inventory } from "@/shared/types/inventory"
import { mockInventory } from "./inventory.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getInventory(): Promise<Inventory[]> {
  await delay(200)
  return mockInventory
}

export async function getInventoryItem(
  id: string,
): Promise<Inventory | undefined> {
  await delay(200)
  return mockInventory.find((i) => i.id === id)
}
