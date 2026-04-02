package neon.core

import scala.concurrent.{ExecutionContext, Future}

/** Convenience alias for asynchronous operations that may fail with a domain error. */
type AsyncResult[E, A] = Future[Either[E, A]]

extension [E, A](fa: AsyncResult[E, A])

  /** Chains an async fallible operation on the success branch. */
  def flatMapR[B](
      f: A => AsyncResult[E, B]
  )(using ExecutionContext): AsyncResult[E, B] =
    fa.flatMap:
      case Left(e)  => Future.successful(Left(e))
      case Right(a) => f(a)

  /** Maps the success branch, preserving the error type. */
  def mapR[B](f: A => B)(using ExecutionContext): AsyncResult[E, B] =
    fa.map(_.map(f))
