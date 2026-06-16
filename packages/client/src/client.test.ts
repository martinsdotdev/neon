import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { createApiClient } from "./client"

type FetchArgs = { url: string; init: RequestInit }

/** Installs a fetch stub that records its call and returns `response`. */
const stubFetch = (response: Response): { calls: FetchArgs[] } => {
  const calls: FetchArgs[] = []
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init: RequestInit) => {
      calls.push({ url, init })
      return Promise.resolve(response)
    })
  )
  return { calls }
}

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("createApiClient", () => {
  describe("success parsing", () => {
    it("parses a JSON body on 200", async () => {
      stubFetch(jsonResponse({ id: "t1" }))
      const client = createApiClient({ baseUrl: "" })
      const result = await client.get<{ id: string }>("/api/tasks/t1")
      expect(result.isOk()).toBe(true)
      expect(result._unsafeUnwrap()).toEqual({ id: "t1" })
    })

    it("returns undefined for a 204 No Content response", async () => {
      stubFetch(new Response(null, { status: 204 }))
      const client = createApiClient({ baseUrl: "" })
      const result = await client.del("/api/tasks/t1")
      expect(result.isOk()).toBe(true)
      expect(result._unsafeUnwrap()).toBeUndefined()
    })
  })

  describe("error parsing", () => {
    it("parses an RFC 9457 problem+json body", async () => {
      const problem = {
        status: 404,
        title: "Task not found",
        type: "urn:neon:error:task-not-found",
      }
      stubFetch(
        new Response(JSON.stringify(problem), {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        })
      )
      const client = createApiClient({ baseUrl: "" })
      const result = await client.get("/api/tasks/missing")
      expect(result.isErr()).toBe(true)
      const error = result._unsafeUnwrapErr()
      expect(error.kind).toBe("problem")
      if (error.kind === "problem") {
        expect(error.problem.title).toBe("Task not found")
        expect(error.problem.type).toBe("urn:neon:error:task-not-found")
      }
    })

    it("parses a plain application/json error body", async () => {
      stubFetch(jsonResponse({ status: 409, title: "Conflict", type: "about:blank" }, 409))
      const client = createApiClient({ baseUrl: "" })
      const result = await client.get("/api/tasks/x")
      const error = result._unsafeUnwrapErr()
      expect(error.kind).toBe("problem")
      if (error.kind === "problem") {
        expect(error.problem.status).toBe(409)
      }
    })

    it("fabricates a problem when the error body is not JSON", async () => {
      stubFetch(new Response("not json", { status: 500, statusText: "Server Error" }))
      const client = createApiClient({ baseUrl: "" })
      const result = await client.get("/api/tasks/x")
      const error = result._unsafeUnwrapErr()
      expect(error.kind).toBe("problem")
      if (error.kind === "problem") {
        expect(error.problem.status).toBe(500)
        expect(error.problem.title).toBe("Server Error")
        expect(error.problem.type).toBe("about:blank")
      }
    })

    it("maps a thrown fetch into a network error", async () => {
      vi.stubGlobal(
        "fetch",
        vi.fn(() => Promise.reject(new Error("connection refused")))
      )
      const client = createApiClient({ baseUrl: "" })
      const result = await client.get("/api/tasks/x")
      const error = result._unsafeUnwrapErr()
      expect(error.kind).toBe("network")
      if (error.kind === "network") {
        expect(error.message).toContain("connection refused")
      }
    })
  })

  describe("auth and credentials", () => {
    it("injects a Bearer token from an async getter and keeps cookies same-origin", async () => {
      const { calls } = stubFetch(jsonResponse({ ok: true }))
      const client = createApiClient({
        baseUrl: "",
        getAuthToken: () => Promise.resolve("secret-token"),
      })
      await client.get("/api/me")
      const headers = calls[0].init.headers as Record<string, string>
      expect(headers.Authorization).toBe("Bearer secret-token")
      expect(calls[0].init.credentials).toBe("same-origin")
    })

    it("sends cookies when no auth token getter is configured", async () => {
      const { calls } = stubFetch(jsonResponse({ ok: true }))
      const client = createApiClient({ baseUrl: "" })
      await client.get("/api/me")
      const headers = calls[0].init.headers as Record<string, string>
      expect(headers.Authorization).toBeUndefined()
      expect(calls[0].init.credentials).toBe("include")
    })

    it("sets Content-Type application/json when a body is sent", async () => {
      const { calls } = stubFetch(jsonResponse({ ok: true }))
      const client = createApiClient({ baseUrl: "" })
      await client.post("/api/tasks", { name: "x" })
      const headers = calls[0].init.headers as Record<string, string>
      expect(headers["Content-Type"]).toBe("application/json")
      expect(calls[0].init.body).toBe(JSON.stringify({ name: "x" }))
    })
  })

  describe("url joining", () => {
    const cases: Array<[string, string, string]> = [
      ["", "/api/tasks", "/api/tasks"],
      ["https://api.test", "/v1", "https://api.test/v1"],
      ["https://api.test/", "/v1", "https://api.test/v1"],
      ["https://api.test", "v1", "https://api.test/v1"],
    ]

    for (const [baseUrl, path, expected] of cases) {
      it(`joins ${baseUrl || "(empty)"} + ${path} -> ${expected}`, async () => {
        const { calls } = stubFetch(jsonResponse({ ok: true }))
        const client = createApiClient({ baseUrl })
        await client.get(path)
        expect(calls[0].url).toBe(expected)
      })
    }
  })
})
