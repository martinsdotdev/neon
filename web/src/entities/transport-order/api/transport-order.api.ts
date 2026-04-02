import type { TransportOrder } from "@/shared/types/transport-order"
import { mockTransportOrders } from "./transport-order.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getTransportOrders(): Promise<
  TransportOrder[]
> {
  await delay(200)
  return mockTransportOrders
}

export async function getTransportOrder(
  id: string,
): Promise<TransportOrder | undefined> {
  await delay(200)
  return mockTransportOrders.find((to) => to.id === id)
}
