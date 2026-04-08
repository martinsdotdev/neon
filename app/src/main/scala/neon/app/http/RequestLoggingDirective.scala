package neon.app.http

import com.github.f4b6a3.uuid.UuidCreator
import net.logstash.logback.argument.StructuredArguments.entries
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{Directive0, ExceptionHandler}
import org.slf4j.{LoggerFactory, MDC}

/** Wide-event HTTP logging directive. Emits one canonical log line per request with accumulated
  * context: method, path, status, duration, trace ID, and optionally user ID and query string.
  *
  * Sets MDC fields (traceId, httpMethod, httpPath) for downstream consumption by service-layer
  * Future chains via MdcExecutionContext.
  */
object RequestLoggingDirective:

  private val logger =
    LoggerFactory.getLogger("neon.http.access")

  def withRequestLogging: Directive0 =
    extractRequest.flatMap { request =>
      val traceId =
        UuidCreator.getTimeOrderedEpoch().toString
      val startNanos = System.nanoTime()
      val previousMdc = Option(MDC.getCopyOfContextMap)

      MDC.put("traceId", traceId)
      MDC.put("httpMethod", request.method.value)
      MDC.put("httpPath", request.uri.path.toString)

      handleExceptions(
        loggingExceptionHandler(
          traceId,
          startNanos,
          previousMdc
        )
      ) & mapResponse { response =>
        val durationMs =
          (System.nanoTime() - startNanos) / 1_000_000

        val status = response.status.intValue
        val fields =
          java.util.LinkedHashMap[String, Any]()
        fields.put("traceId", traceId)
        fields.put("method", request.method.value)
        fields.put("path", request.uri.path.toString)
        fields.put("status", status)
        fields.put("durationMs", durationMs)

        request.uri.rawQueryString.foreach(queryString => fields.put("queryString", queryString))
        Option(MDC.get("userId")).foreach(userId => fields.put("userId", userId))

        val message =
          s"${request.method.value} " +
            s"${request.uri.path} " +
            s"$status ${durationMs}ms"

        if status >= 500 then logger.error(message, entries(fields))
        else if status >= 400 then logger.warn(message, entries(fields))
        else logger.info(message, entries(fields))

        previousMdc.fold(MDC.clear())(MDC.setContextMap)
        response
      }
    }

  private def loggingExceptionHandler(
      traceId: String,
      startNanos: Long,
      previousMdc: Option[java.util.Map[String, String]]
  ): ExceptionHandler =
    ExceptionHandler { case exception: Throwable =>
      extractRequest { request =>
        val durationMs =
          (System.nanoTime() - startNanos) / 1_000_000

        val fields =
          java.util.LinkedHashMap[String, Any]()
        fields.put("traceId", traceId)
        fields.put("method", request.method.value)
        fields.put("path", request.uri.path.toString)
        fields.put("status", 500)
        fields.put("durationMs", durationMs)
        fields.put(
          "errorType",
          exception.getClass.getSimpleName
        )

        logger.error(
          s"${request.method.value} " +
            s"${request.uri.path} 500 ${durationMs}ms",
          entries(fields),
          exception
        )

        previousMdc.fold(MDC.clear())(MDC.setContextMap)
        complete(StatusCodes.InternalServerError)
      }
    }
