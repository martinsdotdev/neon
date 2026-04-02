import type { Sku } from "@/shared/types/sku"
import type { SkuId } from "@/shared/types/ids"

export const mockSkus: Sku[] = [
  {
    id: "0193a5b0-000a-7000-8000-000000000001" as SkuId,
    code: "SKU-001",
    description: "Widget A",
    lotManaged: false,
    uomHierarchy: { Each: 1, Case: 12 },
  },
  {
    id: "0193a5b0-000a-7000-8000-000000000002" as SkuId,
    code: "SKU-002",
    description: "Gadget B",
    lotManaged: true,
    uomHierarchy: {
      Each: 1,
      InnerPack: 6,
      Case: 24,
      Pallet: 480,
    },
  },
]
