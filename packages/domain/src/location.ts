import { z } from "zod"

// Mirrors `order/location/src/main/scala/neon/location/LocationType.scala`.
export const LOCATION_TYPES = [
  "Pick",
  "Reserve",
  "Buffer",
  "Staging",
  "Packing",
  "Dock",
] as const

export type LocationType = (typeof LOCATION_TYPES)[number]

// Mirrors the Location API view served by the backend location routes.
export interface Location {
  code: string
  id: string
  locationType: LocationType
  pickingSequence: number | null
  zoneId: string | null
}

export const LocationTypeSchema = z.enum(LOCATION_TYPES)

export const LocationSchema: z.ZodType<Location> = z.object({
  code: z.string(),
  id: z.string(),
  locationType: LocationTypeSchema,
  pickingSequence: z.number().nullable(),
  zoneId: z.string().nullable(),
})
