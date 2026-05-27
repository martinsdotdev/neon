---
status: "accepted"
date: 2026-04-14
decision-makers: project owner
consulted:
informed: future contributors
---

# Use RFC 9457 Problem Details for API errors

## Context and Problem Statement

The Neon WES API needs a consistent error response format. The backend uses
sealed trait error ADTs with `Either[Error, Result]` return types. The frontend
needs to:

- Distinguish between error types programmatically
- Display appropriate localized error messages
- Handle specific errors differently (e.g., validation vs. authorization)

How should we structure API error responses to provide consistent,
machine-readable error information?

## Decision Drivers

- Frontend needs to programmatically handle different error types
- Error responses must be consistent across all endpoints
- Must support validation errors with field-level detail
- Should follow industry standards for interoperability
- Must map cleanly from the backend's sealed trait error ADTs

## Considered Options

- RFC 9457 Problem Details (the IETF standard; obsoletes RFC 7807)
- Ad-hoc error shape (a custom `{ error, code, ... }` we define and version ourselves)
- Plain HTTP status code + text body

## Decision Outcome

Chosen option: "RFC 9457 Problem Details", because it provides a standardized,
extensible format that maps naturally from the backend's domain error types and
is well-supported by tooling across the industry.

### Consequences

- **Good**, because all errors follow the same structure across the API (consistency).
- **Good**, because the frontend can switch on the `type` URI for specific
  handling (machine-readable).
- **Good**, because it is extensible: custom fields can be added (e.g., `errors`
  for validation details).
- **Good**, because it is a well-documented standard with broad industry adoption.
- **Good**, because it maps naturally from the backend's sealed-trait error ADTs.
- **Bad**, because it requires defining error-type URIs for each error category.
- **Neutral**, because the team must understand RFC 9457 semantics — a one-time
  learning cost.

### Confirmation

The decision will be confirmed when the HTTP API emits `application/problem+json`
for every non-2xx response, every emitted `type` URI is documented, and route
handlers map each domain error ADT to its corresponding problem type.

## Pros and Cons of the Options

### RFC 9457 Problem Details

A registered media type (`application/problem+json`) with standard fields
(`type`, `title`, `status`, `detail`, `instance`) and first-class extension fields.

- **Good**, because it is IETF-standardized and stable; it will not pivot underneath us
- **Good**, because a registered content type ships with it — no invention required
- **Good**, because extension fields are first-class (validation error lists, retry hints)
- **Good**, because middleware and client parsers exist across most stacks
- **Neutral**, because `type` URIs must be kept stable and meaningful — a discipline, not a difficulty

### Ad-hoc error shape

A bespoke `{ error, code, ... }` envelope defined and versioned by the project.

- **Good**, because total freedom over the shape
- **Bad**, because it reinvents a problem the IETF already solved; no off-the-shelf tooling
- **Bad**, because we own the versioning and extension story ourselves

### Plain HTTP status + text body

An HTTP status code plus a human-readable string.

- **Good**, because it is trivially simple
- **Bad**, because it is unstructured; clients cannot reliably distinguish error types programmatically
- **Bad**, because it offers no extension mechanism for correlation IDs or field-level errors

## More Information

### Response Format

```json
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "urn:neon:error:validation",
  "status": 422,
  "title": "Validation Failed",
  "detail": "One or more fields failed validation",
  "instance": "/api/waves/release",
  "errors": [
    { "field": "orderIds", "message": "At least one order is required" }
  ]
}
```

### Standard Fields

| Field      | Type   | Description                                 |
| ---------- | ------ | ------------------------------------------- |
| `type`     | URI    | Identifies the problem category             |
| `status`   | number | HTTP status code (mirrored from response)   |
| `title`    | string | Short, human-readable summary               |
| `detail`   | string | Explanation specific to this occurrence     |
| `instance` | URI    | Identifies this specific problem occurrence |

### Extension Fields

For validation errors:

- `errors`: Array of `{ field, message }` objects for field-level details

For rate limiting:

- `retryAfter`: Seconds until the client can retry

### Error Type URIs

| Type URI                      | Status | Usage                      |
| ----------------------------- | ------ | -------------------------- |
| `about:blank`                 | varies | Generic errors (use title) |
| `urn:neon:error:validation`   | 422    | Request validation failed  |
| `urn:neon:error:unauthorized` | 401    | Authentication required    |
| `urn:neon:error:forbidden`    | 403    | Insufficient permissions   |
| `urn:neon:error:not-found`    | 404    | Resource not found         |
| `urn:neon:error:conflict`     | 409    | Resource conflict          |
| `urn:neon:error:rate-limited` | 429    | Too many requests          |

### References

- RFC 9457: [Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) (obsoletes RFC 7807)
- The backend error model this maps from — see [ADR-0004](0004-use-either-based-error-handling.md).
- The frontend's matching error handling — see [ADR-0014](0014-use-neverthrow-for-service-errors.md).
- HTTP API details: [Chapter 13 — HTTP API](../book/03-the-infrastructure/ch13-http-api.md).
