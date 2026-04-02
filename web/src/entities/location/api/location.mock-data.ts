import type { Location } from "@/shared/types/location"
import type { LocationId, ZoneId } from "@/shared/types/ids"

export const mockLocations: Location[] = [
  {
    id: "0193a5b0-0005-7000-8000-000000000001" as LocationId,
    code: "A-01-01",
    zoneId: "0193a5b0-0006-7000-8000-000000000001" as ZoneId,
    locationType: "Pick",
    pickingSequence: 1,
  },
  {
    id: "0193a5b0-0005-7000-8000-000000000002" as LocationId,
    code: "A-01-02",
    zoneId: "0193a5b0-0006-7000-8000-000000000001" as ZoneId,
    locationType: "Pick",
    pickingSequence: 2,
  },
  {
    id: "0193a5b0-0005-7000-8000-000000000003" as LocationId,
    code: "R-01-01",
    zoneId: null,
    locationType: "Reserve",
    pickingSequence: null,
  },
  {
    id: "0193a5b0-0005-7000-8000-000000000004" as LocationId,
    code: "B-01",
    zoneId: null,
    locationType: "Buffer",
    pickingSequence: null,
  },
]
