import type { UserId } from "./ids"

export interface User {
  id: UserId
  login: string
  name: string
  active: boolean
}
