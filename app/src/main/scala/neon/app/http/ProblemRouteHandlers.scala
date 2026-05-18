package neon.app.http

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.{
  AuthenticationFailedRejection,
  AuthorizationFailedRejection,
  ExceptionHandler,
  MalformedQueryParamRejection,
  MalformedRequestContentRejection,
  MethodRejection,
  MissingHeaderRejection,
  MissingQueryParamRejection,
  RejectionHandler,
  UnsupportedRequestContentTypeRejection,
  ValidationRejection
}

import ProblemDetails.given

/** Route-level handlers that convert Pekko rejections and exceptions into RFC
  * 9457 `application/problem+json` bodies. Wrap the top-level route with
  * `handleRejections + handleExceptions` so every error response carries a
  * structured body that mobile and web clients can parse uniformly.
  */
object ProblemRouteHandlers extends LazyLogging:

  val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle:
        case MalformedRequestContentRejection(message, _) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              StatusCodes.BadRequest,
              "malformed-request",
              "Malformed request body",
              Some(message)
            )
          )
      .handle:
        case MissingQueryParamRejection(parameter) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              StatusCodes.BadRequest,
              "missing-query-parameter",
              "Missing required query parameter",
              Some(s"Expected query parameter '$parameter'")
            )
          )
      .handle:
        case MalformedQueryParamRejection(name, message, _) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              StatusCodes.BadRequest,
              "malformed-query-parameter",
              s"Malformed query parameter '$name'",
              Some(message)
            )
          )
      .handle:
        case MissingHeaderRejection(headerName) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              StatusCodes.BadRequest,
              "missing-header",
              "Missing required header",
              Some(s"Expected header '$headerName'")
            )
          )
      .handle:
        case UnsupportedRequestContentTypeRejection(supported) =>
          complete(
            StatusCodes.UnsupportedMediaType -> ProblemDetails.of(
              StatusCodes.UnsupportedMediaType,
              "unsupported-media-type",
              "Unsupported request content type",
              Some(s"Expected one of: ${supported.mkString(", ")}")
            )
          )
      .handle:
        case ValidationRejection(message, _) =>
          complete(
            StatusCodes.UnprocessableEntity -> ProblemDetails.of(
              StatusCodes.UnprocessableEntity,
              "validation",
              "Request did not satisfy validation rules",
              Some(message)
            )
          )
      .handle:
        case AuthenticationFailedRejection(_, _) =>
          complete(
            StatusCodes.Unauthorized -> ProblemDetails.of(
              StatusCodes.Unauthorized,
              "unauthorized",
              "Authentication required"
            )
          )
      .handle:
        case AuthorizationFailedRejection =>
          complete(
            StatusCodes.Forbidden -> ProblemDetails.of(
              StatusCodes.Forbidden,
              "forbidden",
              "Insufficient permissions for this operation"
            )
          )
      .handleAll[MethodRejection]: rejections =>
        val methods = rejections.map(_.supported.name).mkString(", ")
        complete(
          StatusCodes.MethodNotAllowed -> ProblemDetails.of(
            StatusCodes.MethodNotAllowed,
            "method-not-allowed",
            "HTTP method not allowed for this resource",
            Some(s"Supported methods: $methods")
          )
        )
      .handleNotFound:
        complete(
          StatusCodes.NotFound -> ProblemDetails.of(
            StatusCodes.NotFound,
            "not-found",
            "The requested resource was not found"
          )
        )
      .result()

  val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case throwable =>
      logger.error("Unhandled exception in HTTP route", throwable)
      complete(
        StatusCodes.InternalServerError -> ProblemDetails.of(
          StatusCodes.InternalServerError,
          "internal",
          "An unexpected error occurred"
        )
      )
  }
