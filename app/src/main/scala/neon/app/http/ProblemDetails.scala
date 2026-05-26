package neon.app.http

import io.circe.{Encoder, Json, Printer}
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentType, HttpEntity, MediaType, StatusCode}
import org.apache.pekko.util.ByteString

/** RFC 9457 Problem Details for HTTP APIs. The `type` field is a URI identifying the error kind; we
  * use `urn:neon:error:<slug>` for our own errors and `about:blank` for fully generic responses.
  * See ADR 0011.
  */
final case class ProblemDetails(
    status: Int,
    title: String,
    `type`: String = ProblemDetails.About,
    detail: Option[String] = None,
    instance: Option[String] = None
)

object ProblemDetails:

  val About = "about:blank"

  val MediaTypeName = "application/problem+json"

  val mediaType: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset(
      "problem+json",
      org.apache.pekko.http.scaladsl.model.HttpCharsets.`UTF-8`
    )

  val contentType: ContentType.WithFixedCharset =
    ContentType(mediaType)

  given Encoder[ProblemDetails] = Encoder.instance { problem =>
    val base = Json.obj(
      "status" -> Json.fromInt(problem.status),
      "title" -> Json.fromString(problem.title),
      "type" -> Json.fromString(problem.`type`)
    )
    val withDetail =
      problem.detail.fold(base)(d => base.deepMerge(Json.obj("detail" -> Json.fromString(d))))
    problem.instance.fold(withDetail) { i =>
      withDetail.deepMerge(Json.obj("instance" -> Json.fromString(i)))
    }
  }

  private val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)

  given marshaller: ToEntityMarshaller[ProblemDetails] =
    Marshaller.withFixedContentType(contentType) { problem =>
      val encoder = summon[Encoder[ProblemDetails]]
      HttpEntity.Strict(
        contentType,
        ByteString(printer.print(encoder(problem)))
      )
    }

  /** Convenience for routes that want to emit a problem response without the boilerplate. Pair with
    * `complete(...)` inside a route.
    */
  def of(
      status: StatusCode,
      slug: String,
      title: String,
      detail: Option[String] = None,
      instance: Option[String] = None
  ): ProblemDetails =
    ProblemDetails(
      status = status.intValue,
      title = title,
      `type` = s"urn:neon:error:$slug",
      detail = detail,
      instance = instance
    )
