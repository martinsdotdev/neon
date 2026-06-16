import type { ApiError } from "@neon/domain/error"
import { errAsync, okAsync } from "neverthrow"
import { describe, expect, it } from "vitest"
import { ApiRequestError, unwrapForQuery } from "./query"

const problemError: ApiError = {
  kind: "problem",
  problem: {
    status: 404,
    title: "Task not found",
    type: "urn:neon:error:task-not-found",
  },
}

const networkError: ApiError = { kind: "network", message: "offline" }

describe("unwrapForQuery", () => {
  it("returns the value on success", async () => {
    const value = await unwrapForQuery(okAsync({ id: "t1" }))
    expect(value).toEqual({ id: "t1" })
  })

  it("throws ApiRequestError on failure when no fallback is given", async () => {
    await expect(unwrapForQuery(errAsync(problemError))).rejects.toBeInstanceOf(
      ApiRequestError
    )
  })

  it("carries the structured ApiError on the thrown error", async () => {
    const error = await unwrapForQuery(errAsync(networkError)).catch(
      (caught) => caught as ApiRequestError
    )
    expect(error.apiError).toBe(networkError)
    expect(error.message).toBe("offline")
  })

  it("uses the problem title as the message for problem errors", async () => {
    const error = await unwrapForQuery(errAsync(problemError)).catch(
      (caught) => caught as ApiRequestError
    )
    expect(error.message).toBe("Task not found")
  })

  it("returns the fallback instead of throwing when one is provided", async () => {
    const value = await unwrapForQuery(errAsync<string[], ApiError>(problemError), {
      fallback: ["mock"],
    })
    expect(value).toEqual(["mock"])
  })

  it("returns a null fallback rather than throwing", async () => {
    const value = await unwrapForQuery(errAsync<string | null, ApiError>(problemError), {
      fallback: null,
    })
    expect(value).toBeNull()
  })

  it("throws when the fallback is explicitly undefined (production web case)", async () => {
    await expect(
      unwrapForQuery(errAsync(problemError), { fallback: undefined })
    ).rejects.toBeInstanceOf(ApiRequestError)
  })
})
