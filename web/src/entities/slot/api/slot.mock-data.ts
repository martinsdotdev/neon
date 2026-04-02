import type { Slot } from "@/shared/types/slot"
import type {
  HandlingUnitId,
  OrderId,
  SlotId,
  WorkstationId,
} from "@/shared/types/ids"

export const mockSlots: Slot[] = [
  {
    id: "0193a5b0-000b-7000-8000-000000000001" as SlotId,
    status: "Available",
    workstationId:
      "0193a5b0-0007-7000-8000-000000000001" as WorkstationId,
    orderId: null,
    handlingUnitId: null,
  },
  {
    id: "0193a5b0-000b-7000-8000-000000000002" as SlotId,
    status: "Reserved",
    workstationId:
      "0193a5b0-0007-7000-8000-000000000001" as WorkstationId,
    orderId:
      "0193a5b0-0002-7000-8000-000000000001" as OrderId,
    handlingUnitId:
      "0193a5b0-000c-7000-8000-000000000001" as HandlingUnitId,
  },
  {
    id: "0193a5b0-000b-7000-8000-000000000003" as SlotId,
    status: "Completed",
    workstationId:
      "0193a5b0-0007-7000-8000-000000000002" as WorkstationId,
    orderId:
      "0193a5b0-0002-7000-8000-000000000002" as OrderId,
    handlingUnitId:
      "0193a5b0-000c-7000-8000-000000000002" as HandlingUnitId,
  },
]
