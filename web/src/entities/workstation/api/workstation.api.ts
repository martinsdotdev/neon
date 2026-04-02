import type { Workstation } from "@/shared/types/workstation"
import { mockWorkstations } from "./workstation.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getWorkstations(): Promise<
  Workstation[]
> {
  await delay(200)
  return mockWorkstations
}

export async function getWorkstation(
  id: string,
): Promise<Workstation | undefined> {
  await delay(200)
  return mockWorkstations.find((w) => w.id === id)
}
