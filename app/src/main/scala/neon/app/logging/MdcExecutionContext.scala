package neon.app.logging

import org.slf4j.MDC

import scala.concurrent.ExecutionContext

/** ExecutionContext wrapper that propagates SLF4J MDC across async boundaries. Snapshots the
  * caller's MDC at task submission time and restores it on the executing thread, ensuring trace IDs
  * and other contextual fields survive Future chains.
  */
class MdcExecutionContext(delegate: ExecutionContext) extends ExecutionContext:

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
