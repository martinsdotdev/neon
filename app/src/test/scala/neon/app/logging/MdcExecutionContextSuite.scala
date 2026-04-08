package neon.app.logging

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.MDC

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class MdcExecutionContextSuite extends AnyFunSpec with Matchers:

  private val delegate = ExecutionContext.fromExecutorService(
    java.util.concurrent.Executors.newFixedThreadPool(2)
  )
  private given ExecutionContext = MdcExecutionContext(delegate)

  describe("MdcExecutionContext"):

    it("propagates MDC values into Future bodies"):
      MDC.put("traceId", "abc-123")
      val result = Await.result(
        Future(MDC.get("traceId")),
        5.seconds
      )
      result shouldBe "abc-123"
      MDC.clear()

    it("propagates MDC through flatMap chains"):
      MDC.put("traceId", "chain-456")
      val result = Await.result(
        Future("step1")
          .flatMap(_ => Future(MDC.get("traceId"))),
        5.seconds
      )
      result shouldBe "chain-456"
      MDC.clear()

    it("does not leak MDC between unrelated futures"):
      MDC.put("traceId", "request-1")
      val future1 = Future {
        Thread.sleep(50)
        MDC.get("traceId")
      }
      MDC.clear()
      MDC.put("traceId", "request-2")
      val future2 = Future(MDC.get("traceId"))
      MDC.clear()

      Await.result(future1, 5.seconds) shouldBe "request-1"
      Await.result(future2, 5.seconds) shouldBe "request-2"

    it("handles absent MDC gracefully"):
      MDC.clear()
      val result = Await.result(
        Future(Option(MDC.get("traceId"))),
        5.seconds
      )
      result shouldBe None
