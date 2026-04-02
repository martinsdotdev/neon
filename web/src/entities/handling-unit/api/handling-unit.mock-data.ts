import type { HandlingUnit } from "@/shared/types/handling-unit"
import type {
  HandlingUnitId,
  LocationId,
  OrderId,
} from "@/shared/types/ids"

export const mockHandlingUnits: HandlingUnit[] = [
  {
    id: "0193a5b0-000c-7000-8000-000000000001" as HandlingUnitId,
    status: "PickCreated",
    packagingLevel: "Case",
    currentLocation:
      "0193a5b0-0005-7000-8000-000000000001" as LocationId,
    orderId: null,
  },
  {
    id: "0193a5b0-000c-7000-8000-000000000002" as HandlingUnitId,
    status: "InBuffer",
    packagingLevel: "Case",
    currentLocation:
      "0193a5b0-0005-7000-8000-000000000004" as LocationId,
    orderId: null,
  },
  {
    id: "0193a5b0-000c-7000-8000-000000000003" as HandlingUnitId,
    status: "ShipCreated",
    packagingLevel: "Each",
    currentLocation:
      "0193a5b0-0005-7000-8000-000000000003" as LocationId,
    orderId:
      "0193a5b0-0002-7000-8000-000000000001" as OrderId,
  },
  {
    id: "0193a5b0-000c-7000-8000-000000000004" as HandlingUnitId,
    status: "Shipped",
    packagingLevel: "Each",
    currentLocation: null,
    orderId:
      "0193a5b0-0002-7000-8000-000000000002" as OrderId,
  },
]
