# Use Structured Logging with Wide Events and MDC Propagation

## Status

Proposed

## Context and Problem Statement

Neon WES has virtually no observability: two log statements in the entire
codebase, no logback configuration, no structured logging, no request tracing.
When something goes wrong in production, there is no way to trace a request
through the HTTP layer, services, actors, and projections.

How should we instrument the application for operational visibility?

## Decision Drivers

- Wide events philosophy (loggingsucks.com): one rich structured event per HTTP
  request, not scattered printf-style logging
- Tracing-inspired context propagation (Rust's tracing library): structured
  fields that flow across async boundaries
- Error tracking with context (Sentry-inspired): structured error reporting
  ready for future integration with external services
- Standard JVM ecosystem: prefer boring, proven libraries over exotic choices
- Async safety: MDC must survive Scala Future chains on shared thread pools
- Minimal boilerplate: logging should be easy to add to new code

## Considered Options

- Logback + logstash-logback-encoder + scala-logging (standard JVM stack)
- Scribe (Scala-native logging library)
- OpenTelemetry Java agent (auto-instrumentation)

## Decision Outcome

Chosen option: "Logback + logstash-logback-encoder + scala-logging", because it
is the standard JVM logging stack, integrates cleanly with Pekko's SLF4J-based
logging, and avoids exotic dependencies.

### Consequences

#### Positive

- One canonical log line per HTTP request with trace ID, method, path, status,
  duration, user ID, and query string (wide event pattern)
- Trace IDs (UUID v7) propagate from HTTP through services via
  MdcExecutionContext, enabling cross-layer request tracing
- Actor logging via Behaviors.withMdc provides entity context (entityType,
  entityId) on every actor log line
- Dual logback configuration: colored console for development, structured JSON
  for production, quiet WARN-only for tests
- LoggingProjectionHandler base class eliminates boilerplate across all
  projection handlers
- Structured auth event logging (login success/failure, permission denied)
  without leaking sensitive data

#### Negative

- MdcExecutionContext adds per-task overhead (MDC snapshot/restore); negligible
  at WES throughput but present on every Future submission
- MDC does not propagate into actor message processing (actors use
  Behaviors.withMdc separately); HTTP trace IDs are not visible in actor logs
- scala-logging 4.0.0-RC1 is a release candidate; no final 4.0.0 release exists

### Confirmation

The decision will be confirmed by:

- `MdcExecutionContextSuite`: MDC propagation across Future chains, isolation
  between concurrent requests, graceful handling of absent MDC
- All existing tests pass with logback-test.xml (WARN-only, no noise)
- Manual verification: colored console output in dev, structured JSON in prod

## Pros and Cons of the Options

### Logback + logstash-logback-encoder + scala-logging

Logback 1.5.18 (already present) as SLF4J backend. logstash-logback-encoder 8.1
for structured JSON output and StructuredArguments API. scala-logging 4.0.0-RC1
for LazyLogging trait with macro-generated level guards. Pekko's context.log for
actor logging.

- Good, because this is the standard stack used by Lichess, Play Framework, and
  virtually every production Pekko deployment
- Good, because logstash-logback-encoder's StructuredArguments produce top-level
  JSON fields queryable by any log aggregator (Loki, Elasticsearch, Datadog)
- Good, because Logback 1.5.18 and SLF4J 2.0.17 are already present via Pekko
- Good, because scala-logging's LazyLogging eliminates LoggerFactory boilerplate
- Neutral, because logstash-logback-encoder 8.1, not 9.0 (v9 requires Jackson
  3.0 which conflicts with Pekko's Jackson 2.x serialization)
- Bad, because MdcExecutionContext is a DIY ~20-line wrapper (no maintained
  library exists for Pekko + Scala 3)

### Scribe (Scala-Native Logging)

outr/scribe 3.19.0. Programmatic configuration (no XML). Cross-platform
(JVM, JS, Native). Built-in JSON output via scribe-json module.

- Good, because Scala-native with no XML configuration
- Good, because cross-platform support
- Bad, because SLF4J MDC bridge does not propagate MDC values into JSON output
  (GitHub issue #304), losing Pekko's automatic pekkoSource context
- Bad, because smaller community (549 stars vs logstash-logback-encoder's
  industry-standard status)

### OpenTelemetry Java Agent

Auto-instrumentation via -javaagent. Automatic HTTP spans, context propagation
across async boundaries, trace/span IDs in MDC via OTEL Logback bridge.

- Good, because zero-code-change instrumentation for HTTP and async context
- Good, because industry-standard observability protocol
- Good, because automatic trace/span IDs in log events
- Bad, because Pekko support has known issues (context propagation with `after`
  pattern, issue #11755)
- Bad, because premature for a single-service app with no logging at all;
  structured logging should come first
- Bad, because agent-based approach is opaque and harder to debug

## More Information

### Architecture

```
HTTP Request
  |
  v
RequestLoggingDirective (sets MDC: traceId, httpMethod, httpPath)
  |
  v
AuthDirectives (sets MDC: userId)
  |
  v
Route Handler -> Service (LazyLogging, MDC via MdcExecutionContext)
                    |
                    v
                 Actor (Behaviors.withMdc: entityType, entityId)
                    |
                    v
                 Projection (LoggingProjectionHandler: debug + error recovery)
  |
  v
RequestLoggingDirective (emits canonical log line, restores MDC)
```

### Log Output Examples

Development (colored console):
```
14:23:45.123 INFO  neon.http.access    [0195...] [:] | POST /tasks/.../complete 200 45ms
14:23:45.080 DEBUG neon.task.TaskActor [:] [Task:0195...] | Received Complete in state ActiveState
```

Production (JSON via logstash-logback-encoder):
```json
{"@timestamp":"...","level":"INFO","logger_name":"neon.http.access",
 "message":"POST /tasks/.../complete 200 45ms",
 "traceId":"0195...","method":"POST","path":"/tasks/.../complete",
 "status":200,"durationMs":45,"userId":"..."}
```

### Dependencies

| Library | Version | Purpose |
| --- | --- | --- |
| logstash-logback-encoder | 8.1 | Structured JSON output, StructuredArguments |
| scala-logging | 4.0.0-RC1 | LazyLogging trait, SLF4J 2.x macro guards |
| logback-classic | 1.5.18 | SLF4J backend (already present) |

### MDC Propagation Strategy

- **HTTP layer**: RequestLoggingDirective sets traceId, httpMethod, httpPath
- **Auth layer**: AuthDirectives sets userId (cleanup by RequestLoggingDirective)
- **Service layer**: MdcExecutionContext propagates MDC across Future chains
- **Actor layer**: Behaviors.withMdc sets entityType, entityId per message
- **Projection layer**: LoggingProjectionHandler base class, no MDC (separate
  daemon process threads)

### References

- [loggingsucks.com](https://loggingsucks.com/): wide events / canonical log
  lines philosophy
- [Rust tracing](https://docs.rs/tracing/latest/tracing/): spans, structured
  fields, context propagation
- [Canonical Log Lines (Stripe)](https://stripe.com/blog/canonical-log-lines):
  one event per request pattern
- [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder):
  structured JSON logging for Logback
- [scala-logging](https://github.com/lightbend-labs/scala-logging): Scala SLF4J
  wrapper with macro-based level guards
- [Pekko Typed Logging](https://pekko.apache.org/docs/pekko/current/typed/logging.html):
  Behaviors.withMdc, context.log
