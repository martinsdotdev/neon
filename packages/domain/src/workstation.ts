import { z } from "zod"

export type WorkstationType = "PutWall" | "PackStation"

export type WorkstationMode = "Receiving" | "Picking" | "Counting" | "Relocation"

export type WorkstationState = "Disabled" | "Idle" | "Active"

export interface Workstation {
  createdAt: string
  id: string
  mode: WorkstationMode
  slotCount: number
  state: WorkstationState
  workstationType: WorkstationType
}

export const WorkstationTypeSchema = z.enum(["PutWall", "PackStation"])

export const WorkstationModeSchema = z.enum([
  "Receiving",
  "Picking",
  "Counting",
  "Relocation",
])

export const WorkstationStateSchema = z.enum(["Disabled", "Idle", "Active"])

export const WorkstationSchema: z.ZodType<Workstation> = z.object({
  createdAt: z.string().datetime(),
  id: z.string().uuid(),
  mode: WorkstationModeSchema,
  slotCount: z.number().int().nonnegative(),
  state: WorkstationStateSchema,
  workstationType: WorkstationTypeSchema,
})
