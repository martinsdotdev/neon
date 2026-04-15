# Use RFC 9457 Problem Details for API Errors

## Status

Accepted

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

## Decision Outcome

Chosen option: "RFC 9457 Problem Details", because it provides a standardized,
extensible format that maps naturally from the backend's domain error types and
is well-supported by tooling across the industry.

### Consequences

#### Positive

- Consistency: All errors follow the same structure across the API
- Machine-readable: Frontend can switch on `type` URI for specific handling
- Extensible: Can add custom fields (e.g., `errors` for validation details)
- Standard: Well-documented RFC with broad industry adoption
- Backend alignment: Maps naturally from sealed trait error ADTs

#### Negative

- Overhead: Requires defining error type URIs for each error category
- Learning curve: Team must understand RFC 9457 semantics

## Response Format

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
