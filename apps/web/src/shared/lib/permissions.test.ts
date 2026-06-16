import { describe, expect, it } from "vitest"
import { formatPermissionLabel, useHasPermission } from "./permissions"

describe("formatPermissionLabel", () => {
  it("turns a permission key into a title-cased label", () => {
    expect(formatPermissionLabel("wave:plan")).toBe("Wave Plan")
    expect(formatPermissionLabel("consolidation-group:complete")).toBe(
      "Consolidation Group Complete"
    )
    expect(formatPermissionLabel("user:manage")).toBe("User Manage")
  })
})

describe("useHasPermission", () => {
  const user = {
    userId: "u1",
    login: "operator",
    name: "Operator",
    role: "Operator" as const,
    permissions: ["task:complete", "task:assign"],
  }

  it("returns false when there is no user", () => {
    expect(useHasPermission(null, "task:complete")).toBe(false)
    expect(useHasPermission(undefined, "task:complete")).toBe(false)
  })

  it("returns true only for a held permission", () => {
    expect(useHasPermission(user, "task:complete")).toBe(true)
    expect(useHasPermission(user, "wave:plan")).toBe(false)
  })
})
