import type { TransportOrder } from "@/shared/types/transport-order"
import type {
  HandlingUnitId,
  LocationId,
  TransportOrderId,
} from "@/shared/types/ids"

export const mockTransportOrders: TransportOrder[] = [
  {
    id: "0193a5b0-0009-7000-8000-000000000001" as TransportOrderId,
    status: "Pending",
    handlingUnitId:
      "0193a5b0-000a-7000-8000-000000000001" as HandlingUnitId,
    destination:
      "0193a5b0-0005-7000-8000-000000000007" as LocationId,
    createdAt: "2026-04-01T08:00:00Z",
    updatedAt: "2026-04-01T08:00:00Z",
  },
  {
    id: "0193a5b0-0009-7000-8000-000000000002" as TransportOrderId,
    status: "Confirmed",
    handlingUnitId:
      "0193a5b0-000a-7000-8000-000000000002" as HandlingUnitId,
    destination:
      "0193a5b0-0005-7000-8000-000000000008" as LocationId,
    createdAt: "2026-04-01T07:00:00Z",
    updatedAt: "2026-04-01T08:30:00Z",
  },
  {
    id: "0193a5b0-0009-7000-8000-000000000003" as TransportOrderId,
    status: "Cancelled",
    handlingUnitId:
      "0193a5b0-000a-7000-8000-000000000003" as HandlingUnitId,
    destination:
      "0193a5b0-0005-7000-8000-000000000009" as LocationId,
    createdAt: "2026-03-31T14:00:00Z",
    updatedAt: "2026-03-31T14:30:00Z",
  },
]
