import { describe, expect, it } from "vitest"
import { PERMISSION_KEYS, PermissionSchema } from "./auth"

describe("PERMISSION_KEYS", () => {
  it("contains no duplicates", () => {
    expect(new Set(PERMISSION_KEYS).size).toBe(PERMISSION_KEYS.length)
  })

  it("uses the domain:action key format", () => {
    for (const key of PERMISSION_KEYS) {
      expect(key).toMatch(/^[a-z-]+:[a-z-]+$/)
    }
  })

  it("backs the PermissionSchema", () => {
    expect(PermissionSchema.options).toEqual([...PERMISSION_KEYS])
  })
})
