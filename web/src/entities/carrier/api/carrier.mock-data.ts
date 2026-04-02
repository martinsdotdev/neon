import type { Carrier } from "@/shared/types/carrier"
import type { CarrierId } from "@/shared/types/ids"

export const mockCarriers: Carrier[] = [
  {
    id: "0193a5b0-0009-7000-8000-000000000001" as CarrierId,
    code: "FDX",
    name: "FedEx",
    active: true,
  },
  {
    id: "0193a5b0-0009-7000-8000-000000000002" as CarrierId,
    code: "UPS",
    name: "UPS",
    active: true,
  },
  {
    id: "0193a5b0-0009-7000-8000-000000000003" as CarrierId,
    code: "DHL",
    name: "DHL",
    active: true,
  },
  {
    id: "0193a5b0-0009-7000-8000-000000000004" as CarrierId,
    code: "USPS",
    name: "USPS",
    active: false,
  },
]
