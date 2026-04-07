package neon.app.http

import io.circe.{Decoder, Encoder, Printer}
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

/** Pekko HTTP marshallers for circe JSON encoding/decoding. */
object CirceSupport:

  private val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)

  given [A: Encoder]: ToEntityMarshaller[A] =
    Marshaller.withFixedContentType(ContentTypes.`application/json`) { a =>
      HttpEntity(ContentTypes.`application/json`, printer.print(a.asJson))
    }

  given [A: Decoder]: FromEntityUnmarshaller[A] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map { str =>
        decode[A](str) match
          case Right(value) => value
          case Left(error)  => throw error
      }
