package neon.app.repository

import io.r2dbc.spi.{Connection, ConnectionFactory, Row}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Thin wrapper around Pekko's [[R2dbcSession]] managing connection lifecycle. Delegates actual
  * query execution to the session's `selectOne`, `select`, and `updateOne` methods.
  */
object R2dbcHelper:

  /** Executes a parameterized query returning at most one row. */
  def queryOne[T](
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(mapRow: Row => T)(using ActorSystem[?], ExecutionContext): Future[Option[T]] =
    withSession(connectionFactory) { session =>
      val stmt = bindParams(session.createStatement(sql), params)
      session.selectOne(stmt)(mapRow)
    }

  /** Executes a parameterized query returning all matching rows. */
  def queryList[T](
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(mapRow: Row => T)(using ActorSystem[?], ExecutionContext): Future[List[T]] =
    withSession(connectionFactory) { session =>
      session.select(bindParams(session.createStatement(sql), params))(mapRow).map(_.toList)
    }

  /** Executes an update/insert statement returning the affected row count. */
  def execute(
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(using ActorSystem[?], ExecutionContext): Future[Long] =
    withSession(connectionFactory) { session =>
      val stmt = bindParams(session.createStatement(sql), params)
      session.updateOne(stmt).map(_.toLong)
    }

  /** Executes a block within an R2DBC transaction. Commits on success, rolls back on failure.
    */
  def withTransaction[T](connectionFactory: ConnectionFactory)(
      f: Connection => Future[T]
  )(using system: ActorSystem[?], ec: ExecutionContext): Future[T] =
    acquireConnection(connectionFactory).flatMap { connection =>
      toFuture(connection.beginTransaction())
        .flatMap(_ => f(connection))
        .flatMap { result =>
          toFuture(connection.commitTransaction())
            .flatMap(_ => toFuture(connection.close()).map(_ => result))
        }
        .recoverWith { case ex =>
          toFuture(connection.rollbackTransaction())
            .flatMap(_ => toFuture(connection.close()))
            .flatMap(_ => Future.failed(ex))
        }
    }

  private def withSession[T](connectionFactory: ConnectionFactory)(
      f: R2dbcSession => Future[T]
  )(using system: ActorSystem[?], ec: ExecutionContext): Future[T] =
    acquireConnection(connectionFactory).flatMap { connection =>
      val session = new R2dbcSession(connection)(using ec, system)
      f(session).andThen { case _ =>
        toFuture(connection.close())
      }
    }

  private def bindParams(
      statement: io.r2dbc.spi.Statement,
      params: Seq[Any]
  ): io.r2dbc.spi.Statement =
    params.zipWithIndex.foldLeft(statement) { case (stmt, (param, idx)) =>
      param match
        case v: UUID    => stmt.bind(idx, v)
        case v: String  => stmt.bind(idx, v)
        case v: Int     => stmt.bind(idx, v: java.lang.Integer)
        case v: Long    => stmt.bind(idx, v: java.lang.Long)
        case v: Boolean => stmt.bind(idx, v: java.lang.Boolean)
        case null       => stmt.bindNull(idx, classOf[Object])
        case other      => stmt.bind(idx, other)
    }

  private def acquireConnection(
      connectionFactory: ConnectionFactory
  )(using system: ActorSystem[?], ec: ExecutionContext): Future[Connection] =
    Source.fromPublisher(connectionFactory.create()).runWith(Sink.head)

  private def toFuture[T](
      publisher: org.reactivestreams.Publisher[T]
  )(using system: ActorSystem[?], ec: ExecutionContext): Future[T] =
    Source.fromPublisher(publisher).runWith(Sink.head)
