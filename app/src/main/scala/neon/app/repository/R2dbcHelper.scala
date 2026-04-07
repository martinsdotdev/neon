package neon.app.repository

import io.r2dbc.spi.{Connection, ConnectionFactory, Result, Row, Statement}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Minimal R2DBC query utilities wrapping the reactive streams API into Scala Futures.
  */
object R2dbcHelper:

  /** Executes a parameterized query returning at most one row. */
  def queryOne[T](
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(mapRow: Row => T)(using ExecutionContext): Future[Option[T]] =
    withConnection(connectionFactory) { connection =>
      val statement = bindParams(connection.createStatement(sql), params)
      collectRows(statement.execute(), mapRow).map(_.headOption)
    }

  /** Executes a parameterized query returning all matching rows. */
  def queryList[T](
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(mapRow: Row => T)(using ExecutionContext): Future[List[T]] =
    withConnection(connectionFactory) { connection =>
      val statement = bindParams(connection.createStatement(sql), params)
      collectRows(statement.execute(), mapRow)
    }

  /** Executes an update/insert statement returning the affected row count. */
  def execute(
      connectionFactory: ConnectionFactory,
      sql: String,
      params: Any*
  )(using ExecutionContext): Future[Long] =
    withConnection(connectionFactory) { connection =>
      val statement = bindParams(connection.createStatement(sql), params)
      toFuture(statement.execute()).flatMap { result =>
        toFuture(result.getRowsUpdated).map(_.longValue)
      }
    }

  private def withConnection[T](connectionFactory: ConnectionFactory)(
      f: Connection => Future[T]
  )(using ExecutionContext): Future[T] =
    toFuture(connectionFactory.create()).flatMap { connection =>
      f(connection).andThen { case _ =>
        toFuture(connection.close())
      }
    }

  /** Executes a block within an R2DBC transaction. Commits on success, rolls back on failure.
    */
  def withTransaction[T](connectionFactory: ConnectionFactory)(
      f: Connection => Future[T]
  )(using ExecutionContext): Future[T] =
    toFuture(connectionFactory.create()).flatMap { connection =>
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

  private def bindParams(statement: Statement, params: Seq[Any]): Statement =
    params.zipWithIndex.foldLeft(statement) { case (stmt, (param, idx)) =>
      param match
        case v: UUID    => stmt.bind(idx, v)
        case v: String  => stmt.bind(idx, v)
        case v: Int     => stmt.bind(idx, v)
        case v: Long    => stmt.bind(idx, v)
        case v: Boolean => stmt.bind(idx, v)
        case null       => stmt.bindNull(idx, classOf[Object])
        case other      => stmt.bind(idx, other)
    }

  private def collectRows[T](
      resultPublisher: Publisher[? <: Result],
      mapRow: Row => T
  )(using ExecutionContext): Future[List[T]] =
    val promise = Promise[List[T]]()
    val rows = scala.collection.mutable.ListBuffer[T]()

    resultPublisher.subscribe(new Subscriber[Result]:
      private var subscription: Subscription = scala.compiletime.uninitialized

      override def onSubscribe(s: Subscription): Unit =
        subscription = s
        s.request(Long.MaxValue)

      override def onNext(result: Result): Unit =
        result
          .map((row, _) => rows += mapRow(row))
          .subscribe(new Subscriber[Object]:
            override def onSubscribe(s: Subscription): Unit =
              s.request(Long.MaxValue)
            override def onNext(t: Object): Unit = ()
            override def onError(t: Throwable): Unit =
              promise.tryFailure(t)
            override def onComplete(): Unit = ())

      override def onError(t: Throwable): Unit =
        promise.tryFailure(t)

      override def onComplete(): Unit =
        promise.trySuccess(rows.toList))

    promise.future

  private def toFuture[T](publisher: Publisher[T])(using
      ExecutionContext
  ): Future[T] =
    val promise = Promise[T]()
    publisher.subscribe(new Subscriber[T]:
      override def onSubscribe(s: Subscription): Unit =
        s.request(1)
      override def onNext(t: T): Unit =
        promise.trySuccess(t)
      override def onError(t: Throwable): Unit =
        promise.tryFailure(t)
      override def onComplete(): Unit =
        if !promise.isCompleted then
          promise.tryFailure(
            java.util.NoSuchElementException("Publisher completed without emitting")
          ))
    promise.future
