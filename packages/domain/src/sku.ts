import { z } from "zod"

// Mirrors the Sku API view (`sku/src/main/scala/neon/sku/Sku.scala` minus the
// UOM hierarchy, which the read endpoints do not expose).
export interface Sku {
  code: string
  description: string
  id: string
  lotManaged: boolean
}

export const SkuSchema: z.ZodType<Sku> = z.object({
  code: z.string(),
  description: z.string(),
  id: z.string(),
  lotManaged: z.boolean(),
})
