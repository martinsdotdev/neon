import type { CarrierId } from "./ids"

export interface Carrier {
  id: CarrierId
  code: string
  name: string
  active: boolean
}
