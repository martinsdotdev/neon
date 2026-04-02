import type { Order } from "@/shared/types/order"
import { mockOrders } from "./order.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getOrders(): Promise<Order[]> {
  await delay(200)
  return mockOrders
}

export async function getOrder(
  id: string,
): Promise<Order | undefined> {
  await delay(200)
  return mockOrders.find((o) => o.id === id)
}
