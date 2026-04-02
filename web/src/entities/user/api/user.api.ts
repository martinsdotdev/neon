import type { User } from "@/shared/types/user"
import { mockUsers } from "./user.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getUsers(): Promise<User[]> {
  await delay(200)
  return mockUsers
}

export async function getUser(
  id: string,
): Promise<User | undefined> {
  await delay(200)
  return mockUsers.find((u) => u.id === id)
}
