import type { LocationId, ZoneId } from "./ids"
import type { LocationType } from "./enums"

export interface Location {
  id: LocationId
  code: string
  zoneId: ZoneId | null
  locationType: LocationType
  pickingSequence: number | null
}

export interface Zone {
  id: ZoneId
  code: string
  description: string
}
