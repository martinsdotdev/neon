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

/** Route-level handlers that convert Pekko rejections and exceptions into RFC 9457
  * `application/problem+json` bodies. Wrap the top-level route with
  * `handleRejections + handleExceptions` so every error response carries a structured body that
  * mobile and web clients can parse uniformly.
  */
object ProblemRouteHandlers extends LazyLogging:

  val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle:
        case MalformedRequestContentRejection(message, _) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              status = StatusCodes.BadRequest,
              slug = "malformed-request",
              title = "Malformed request body",
              detail = Some(message)
            )
          )
      .handle:
        case MissingQueryParamRejection(parameter) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              status = StatusCodes.BadRequest,
              slug = "missing-query-parameter",
              title = "Missing required query parameter",
              detail = Some(s"Expected query parameter '$parameter'")
            )
          )
      .handle:
        case MalformedQueryParamRejection(name, message, _) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              status = StatusCodes.BadRequest,
              slug = "malformed-query-parameter",
              title = s"Malformed query parameter '$name'",
              detail = Some(message)
            )
          )
      .handle:
        case MissingHeaderRejection(headerName) =>
          complete(
            StatusCodes.BadRequest -> ProblemDetails.of(
              status = StatusCodes.BadRequest,
              slug = "missing-header",
              title = "Missing required header",
              detail = Some(s"Expected header '$headerName'")
            )
          )
      .handle:
        case UnsupportedRequestContentTypeRejection(supported) =>
          complete(
            StatusCodes.UnsupportedMediaType -> ProblemDetails.of(
              status = StatusCodes.UnsupportedMediaType,
              slug = "unsupported-media-type",
              title = "Unsupported request content type",
              detail = Some(s"Expected one of: ${supported.mkString(", ")}")
            )
          )
      .handle:
        case ValidationRejection(message, _) =>
          complete(
            StatusCodes.UnprocessableEntity -> ProblemDetails.of(
              status = StatusCodes.UnprocessableEntity,
              slug = "validation",
              title = "Request did not satisfy validation rules",
              detail = Some(message)
            )
          )
      .handle:
        case AuthenticationFailedRejection(_, _) =>
          complete(
            StatusCodes.Unauthorized -> ProblemDetails.of(
              status = StatusCodes.Unauthorized,
              slug = "unauthorized",
              title = "Authentication required"
            )
          )
      .handle:
        case AuthorizationFailedRejection =>
          complete(
            StatusCodes.Forbidden -> ProblemDetails.of(
              status = StatusCodes.Forbidden,
              slug = "forbidden",
              title = "Insufficient permissions for this operation"
            )
          )
      .handleAll[MethodRejection]: rejections =>
        val methods = rejections.map(_.supported.name).mkString(", ")
        complete(
          StatusCodes.MethodNotAllowed -> ProblemDetails.of(
            status = StatusCodes.MethodNotAllowed,
            slug = "method-not-allowed",
            title = "HTTP method not allowed for this resource",
            detail = Some(s"Supported methods: $methods")
          )
        )
      .handleNotFound:
        complete(
          StatusCodes.NotFound -> ProblemDetails.of(
            status = StatusCodes.NotFound,
            slug = "not-found",
            title = "The requested resource was not found"
          )
        )
      .result()

  val exceptionHandler: ExceptionHandler = ExceptionHandler { case throwable =>
    logger.error("Unhandled exception in HTTP route", throwable)
    complete(
      StatusCodes.InternalServerError -> ProblemDetails.of(
        status = StatusCodes.InternalServerError,
        slug = "internal",
        title = "An unexpected error occurred"
      )
    )
  }
