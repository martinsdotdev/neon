import type { Location } from "@/shared/types/location"
import { mockLocations } from "./location.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getLocations(): Promise<Location[]> {
  await delay(200)
  return mockLocations
}

export async function getLocation(
  id: string,
): Promise<Location | undefined> {
  await delay(200)
  return mockLocations.find((l) => l.id === id)
}
