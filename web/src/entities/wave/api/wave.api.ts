import type { Wave } from "@/shared/types/wave"
import { mockWaves } from "./wave.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getWaves(): Promise<Wave[]> {
  await delay(200)
  return mockWaves
}

export async function getWave(
  id: string,
): Promise<Wave | undefined> {
  await delay(200)
  return mockWaves.find((w) => w.id === id)
}
