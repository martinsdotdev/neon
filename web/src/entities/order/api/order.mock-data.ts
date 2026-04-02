import type { Order } from "@/shared/types/order"
import type {
  CarrierId,
  OrderId,
  SkuId,
} from "@/shared/types/ids"

export const mockOrders: Order[] = [
  {
    id: "0193a5b0-0002-7000-8000-000000000001" as OrderId,
    priority: "Normal",
    lines: [
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000001" as SkuId,
        packagingLevel: "Each",
        quantity: 5,
      },
    ],
    carrierId:
      "0193a5b0-0009-7000-8000-000000000001" as CarrierId,
  },
  {
    id: "0193a5b0-0002-7000-8000-000000000002" as OrderId,
    priority: "High",
    lines: [
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000001" as SkuId,
        packagingLevel: "Case",
        quantity: 2,
      },
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000002" as SkuId,
        packagingLevel: "Each",
        quantity: 10,
      },
    ],
    carrierId:
      "0193a5b0-0009-7000-8000-000000000002" as CarrierId,
  },
  {
    id: "0193a5b0-0002-7000-8000-000000000003" as OrderId,
    priority: "Critical",
    lines: [
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000002" as SkuId,
        packagingLevel: "InnerPack",
        quantity: 3,
      },
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000001" as SkuId,
        packagingLevel: "Each",
        quantity: 8,
      },
      {
        skuId:
          "0193a5b0-000a-7000-8000-000000000002" as SkuId,
        packagingLevel: "Case",
        quantity: 1,
      },
    ],
    carrierId: null,
  },
]
