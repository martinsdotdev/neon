import type { Task } from "@/shared/types/task"
import { mockTasks } from "./task.mock-data"

const delay = (ms: number) =>
  new Promise((r) => setTimeout(r, ms))

export async function getTasks(): Promise<Task[]> {
  await delay(200)
  return mockTasks
}

export async function getTask(
  id: string,
): Promise<Task | undefined> {
  await delay(200)
  return mockTasks.find((t) => t.id === id)
}
