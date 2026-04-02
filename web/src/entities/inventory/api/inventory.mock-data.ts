import type { Inventory } from "@/shared/types/inventory"
import type {
  InventoryId,
  LocationId,
  SkuId,
} from "@/shared/types/ids"

export const mockInventory: Inventory[] = [
  {
    id: "0193a5b0-000d-7000-8000-000000000001" as InventoryId,
    locationId:
      "0193a5b0-0005-7000-8000-000000000001" as LocationId,
    skuId:
      "0193a5b0-000a-7000-8000-000000000001" as SkuId,
    packagingLevel: "Each",
    lot: null,
    onHand: 50,
    reserved: 10,
    available: 40,
  },
  {
    id: "0193a5b0-000d-7000-8000-000000000002" as InventoryId,
    locationId:
      "0193a5b0-0005-7000-8000-000000000002" as LocationId,
    skuId:
      "0193a5b0-000a-7000-8000-000000000002" as SkuId,
    packagingLevel: "Case",
    lot: "LOT-2026-A",
    onHand: 24,
    reserved: 6,
    available: 18,
  },
  {
    id: "0193a5b0-000d-7000-8000-000000000003" as InventoryId,
    locationId:
      "0193a5b0-0005-7000-8000-000000000003" as LocationId,
    skuId:
      "0193a5b0-000a-7000-8000-000000000001" as SkuId,
    packagingLevel: "Pallet",
    lot: null,
    onHand: 480,
    reserved: 0,
    available: 480,
  },
]
