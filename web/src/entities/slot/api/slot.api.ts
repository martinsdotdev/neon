import type { Slot } from "@/shared/types/slot"
import { mockSlots } from "./slot.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getSlots(): Promise<Slot[]> {
  await delay(200)
  return mockSlots
}

export async function getSlot(
  id: string,
): Promise<Slot | undefined> {
  await delay(200)
  return mockSlots.find((s) => s.id === id)
}
