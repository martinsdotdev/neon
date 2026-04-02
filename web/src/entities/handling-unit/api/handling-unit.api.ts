import type { HandlingUnit } from "@/shared/types/handling-unit"
import { mockHandlingUnits } from "./handling-unit.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getHandlingUnits(): Promise<
  HandlingUnit[]
> {
  await delay(200)
  return mockHandlingUnits
}

export async function getHandlingUnit(
  id: string,
): Promise<HandlingUnit | undefined> {
  await delay(200)
  return mockHandlingUnits.find((hu) => hu.id === id)
}
