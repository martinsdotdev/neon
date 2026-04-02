import type { Carrier } from "@/shared/types/carrier"
import { mockCarriers } from "./carrier.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getCarriers(): Promise<Carrier[]> {
  await delay(200)
  return mockCarriers
}

export async function getCarrier(
  id: string,
): Promise<Carrier | undefined> {
  await delay(200)
  return mockCarriers.find((c) => c.id === id)
}
