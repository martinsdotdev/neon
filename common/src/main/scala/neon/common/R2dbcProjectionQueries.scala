package neon.common

import io.r2dbc.spi.ConnectionFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Shared helper for querying CQRS read-side projection tables via raw R2DBC + Pekko Streams. Used
  * by PekkoXxxRepository implementations for cross-entity queries.
  */
trait R2dbcProjectionQueries:

  protected def connectionFactory: ConnectionFactory
  protected given system: ActorSystem[?]
  protected given ec: ExecutionContext

  protected def queryProjectionIds(
      sql: String,
      param: Any,
      idColumn: String
  ): Future[List[UUID]] =
    queryProjectionIds(sql, List(param), idColumn)

  protected def queryProjectionIds(
      sql: String,
      params: List[Any],
      idColumn: String
  ): Future[List[UUID]] =
    Source
      .fromPublisher(connectionFactory.create())
      .runWith(Sink.headOption)
      .flatMap {
        case None =>
          Future.failed(
            new IllegalStateException(
              "Failed to acquire R2DBC connection"
            )
          )
        case Some(connection) =>
          val stmt = connection.createStatement(sql)
          params.zipWithIndex.foreach { (param, index) =>
            stmt.bind(index, param)
          }
          Source
            .fromPublisher(stmt.execute())
            .flatMapConcat { result =>
              Source.fromPublisher(
                result.map((row, _) => row.get(idColumn, classOf[UUID]))
              )
            }
            .runWith(Sink.seq)
            .map(_.toList)
            .andThen { case _ =>
              Source
                .fromPublisher(connection.close())
                .runWith(Sink.ignore)
            }
      }
