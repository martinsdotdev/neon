# Observability and Logging

A system you cannot observe is a system you cannot operate. In this chapter,
we will build the observability layer of Neon WES, following the "wide
event" philosophy across three layers: HTTP, services, and actors. The
result is a system where every request leaves a structured trail from the
route handler all the way into the event-sourced actor.


## The Wide Events Philosophy

Traditional logging scatters `printf`-style messages throughout the
codebase. In a concurrent system with multiple requests in flight, the
messages interleave and become nearly useless.

The wide events philosophy (sometimes called "canonical log lines," as
described by Stripe's engineering blog) takes a different approach: emit
**one rich structured event per HTTP request** that contains all the
context you need. A single line tells you what endpoint was called, who
called it, how long it took, and whether it succeeded. In structured JSON
form (which we use in production), every field is independently queryable.

> **Note:** The wide events philosophy is documented in
> [ADR-0010](../docs/decisions/0010-use-structured-logging-with-wide-events.md).
> The core references are loggingsucks.com for the philosophy and Stripe's
> "Canonical Log Lines" blog post for the practical pattern.

Neon WES implements wide events through three cooperating pieces:
`RequestLoggingDirective` at the HTTP boundary, `MdcExecutionContext` for
async propagation, and `Behaviors.withMdc` for actor context. Let's examine
each one.


## MDC Propagation Across Three Layers

The SLF4J Mapped Diagnostic Context (MDC) is a thread-local map of
key-value pairs. Any code running on the current thread can read MDC values,
and logging frameworks include them automatically in every log line. The
challenge in an async system is that MDC is thread-local, but `Future`
chains hop between threads. Neon WES solves this with a three-layer
propagation strategy.


### Layer 1: RequestLoggingDirective

The first layer sets the MDC at the HTTP boundary. Every request passes
through `RequestLoggingDirective`, which generates a trace ID and
populates the MDC:

```scala
def withRequestLogging: Directive0 =
  extractRequest.flatMap { request =>
    val traceId = UuidCreator.getTimeOrderedEpoch().toString
    val startNanos = System.nanoTime()

    MDC.put("traceId", traceId)
    MDC.put("httpMethod", request.method.value)
    MDC.put("httpPath", request.uri.path.toString)

    handleExceptions(loggingExceptionHandler(...)) &
      mapResponse { response =>
        val durationMs =
          (System.nanoTime() - startNanos) / 1_000_000
        // ... emit canonical log line with all fields ...
        response
      }
  }
```

<small>*File: app/src/main/scala/neon/app/http/RequestLoggingDirective.scala*</small>

Three things happen here. First, a UUID v7 trace ID is generated for every
request (time-ordered for chronological sorting). Second, MDC fields
(`traceId`, `httpMethod`, `httpPath`) are set so that all downstream log
statements include them. Third, the `mapResponse` callback fires after the
route handler completes, emitting a single structured log line with the
accumulated fields (including `userId`, which `AuthDirectives` adds during
authentication). Log levels are chosen by status code: INFO for 2xx/3xx,
WARN for 4xx, ERROR for 5xx.


### Layer 2: MdcExecutionContext

The second layer ensures that MDC values survive `Future` chains. Scala
`Future` operations (`map`, `flatMap`, `recover`) can execute on any thread
in the pool. When a `Future` switches threads, the MDC on the new thread is
empty, and the trace ID is lost.

`MdcExecutionContext` solves this by wrapping every task submission:

```scala
class MdcExecutionContext(delegate: ExecutionContext)
    extends ExecutionContext:

  override def execute(runnable: Runnable): Unit =
    val callerMdc = Option(MDC.getCopyOfContextMap)
    delegate.execute(() =>
      val previous = Option(MDC.getCopyOfContextMap)
      try
        callerMdc.fold(MDC.clear())(MDC.setContextMap)
        runnable.run()
      finally previous.fold(MDC.clear())(MDC.setContextMap)
    )

  override def reportFailure(cause: Throwable): Unit =
    delegate.reportFailure(cause)
```

<small>*File: app/src/main/scala/neon/app/logging/MdcExecutionContext.scala*</small>

The pattern is snapshot-restore:

1. **On submission:** Snapshot the caller's MDC (`callerMdc`).
2. **On execution:** Save the executing thread's current MDC (`previous`),
   install the caller's MDC, run the task.
3. **On completion:** Restore the executing thread's original MDC.

This means that when a service method logs inside a `Future.flatMap`, the
log line carries the `traceId` that was set by `RequestLoggingDirective`,
even though the code is running on a different thread.

> **Note:** `MdcExecutionContext` adds a small overhead per task: one MDC
> copy on submission and one save/restore on execution. At WES throughput
> levels (hundreds to low thousands of requests per second), this overhead
> is negligible. For extremely high-throughput systems, you might consider
> context-passing approaches instead.


### Layer 3: Behaviors.withMdc for Actors

The third layer provides context inside Pekko actors. Actors process messages
on their own dispatcher threads, which are separate from the HTTP thread pool.
The MDC propagated by `MdcExecutionContext` does not reach them. Instead,
each actor sets its own MDC fields via `Behaviors.withMdc`:

```scala
def apply(entityId: String): Behavior[Command] =
  Behaviors.withMdc[Command](
    Map("entityType" -> "Wave", "entityId" -> entityId)
  ):
    Behaviors.setup: context =>
      EventSourcedBehavior
        .withEnforcedReplies[Command, WaveEvent, State](...)
```

<small>*File: wave/src/main/scala/neon/wave/WaveActor.scala*</small>

Every actor in the system follows this pattern. The `Behaviors.withMdc` call
takes a static map of MDC fields that are installed before every message is
processed and cleared afterward. Every log line emitted by the actor
includes `entityType` (e.g., "Wave", "Task") and `entityId` (the specific
entity's persistence ID).

All 14 event-sourced actors in Neon WES use this pattern.

> **Note:** There is a gap between Layer 2 and Layer 3. The HTTP trace ID
> does not automatically flow into actor log lines, because actor messages
> do not carry MDC context. If you need end-to-end trace correlation from
> HTTP to actor, you would add the trace ID as a field on the actor command
> and include it in the `Behaviors.withMdc` map. Neon WES currently relies
> on correlating by timestamp and entity ID.


## Structured JSON in Production

The development and production environments use different logback
configurations. The development configuration uses a colored console
appender for human readability:

```xml
<appender name="CONSOLE"
          class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>
      %d{HH:mm:ss.SSS} %highlight(%-5level)
      %cyan(%-40logger{40})
      [%X{traceId:-}]
      [%X{entityType:-}:%X{entityId:-}]
      | %msg%n
    </pattern>
  </encoder>
</appender>
```

<small>*File: app/src/main/resources/logback.xml*</small>

The pattern includes MDC fields inline: `[%X{traceId:-}]` prints the trace
ID (or nothing if absent), and `[%X{entityType:-}:%X{entityId:-}]` prints
the actor context.

The production configuration (`logback-prod.xml`) replaces the console
appender with `LogstashEncoder`, which emits structured JSON:

```json
{
  "@timestamp": "2026-04-14T14:23:45.123Z",
  "level": "INFO",
  "logger_name": "neon.http.access",
  "message": "POST /tasks/.../complete 200 45ms",
  "traceId": "0195def...",
  "method": "POST",
  "path": "/tasks/.../complete",
  "status": 200,
  "durationMs": 45,
  "userId": "0195..."
}
```

This JSON is directly ingestible by Loki, Elasticsearch, Datadog, or any
other log aggregator. The production appender is wrapped in an
`AsyncAppender` with `neverBlock=true`, ensuring log output never blocks
application threads.


## Dev vs. Prod Configuration

| Configuration | File | Appender | Level | Purpose |
|--------------|------|----------|-------|---------|
| Development | `logback.xml` | Colored console | neon=DEBUG, root=INFO | Human-readable, verbose |
| Production | `logback-prod.xml` | Async JSON | neon=INFO, root=WARN | Machine-parseable, performant |
| Test | `logback-test.xml` | Plain console | root=WARN | Quiet, only errors during tests |

Tests produce no log output unless something goes wrong at WARN level or
above. Switching between configurations uses the standard Logback mechanism:
`logback.xml` is the default, and production deploys set
`-Dlogback.configurationFile=logback-prod.xml` as a JVM argument.


## Projection Logging

CQRS projection handlers have their own logging concern. Each handler
processes events asynchronously on daemon threads managed by
`ShardedDaemonProcess`. The `LoggingProjectionHandler` base class
standardizes logging across all 14 projection handlers:

```scala
abstract class LoggingProjectionHandler[E](using
    ExecutionContext
) extends R2dbcHandler[EventEnvelope[E]]
    with LazyLogging:

  final override def process(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done] =
    logger.debug(
      "Processing {} for {}",
      envelope.event.getClass.getSimpleName,
      envelope.persistenceId
    )
    processEvent(session, envelope).recoverWith {
      case exception =>
        logger.error(
          "Projection failed for {}",
          envelope.persistenceId,
          exception
        )
        Future.failed(exception)
    }

  protected def processEvent(
      session: R2dbcSession,
      envelope: EventEnvelope[E]
  ): Future[Done]
```

<small>*File: app/src/main/scala/neon/app/projection/LoggingProjectionHandler.scala*</small>

The base class provides two things:

1. **DEBUG logging on every event.** The event class name and persistence ID
   are logged before processing. This creates an audit trail of every event
   the projection consumes.

2. **ERROR logging on failure with stack trace.** If `processEvent` fails,
   the error is logged with the persistence ID and full exception before the
   failure is propagated. Pekko's projection infrastructure handles retries
   and restart; the handler just needs to report what went wrong.

Subclasses implement `processEvent` and get logging for free. All 14
projection handlers in Neon WES extend this base class.


## Summary

- **Wide events:** one canonical log line per HTTP request with trace ID,
  method, path, status, duration, and user ID.
- **MDC propagation in three layers:** `RequestLoggingDirective` at the
  HTTP boundary, `MdcExecutionContext` across `Future` chains,
  `Behaviors.withMdc` in actors.
- **Dual configuration:** colored console for dev, structured JSON for
  production, quiet WARN-only for tests.
- **LoggingProjectionHandler:** base class providing DEBUG entry logging
  and ERROR failure logging for all projection handlers.


## What Comes Next

In the next chapter, we will bring together everything we have learned by
building a complete new aggregate module from scratch: domain type, events,
actor, projection, HTTP routes, and the observability patterns from this
chapter.
