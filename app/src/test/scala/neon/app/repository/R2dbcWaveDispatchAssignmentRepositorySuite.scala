package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.{LocationId, WaveId}
import neon.core.WaveDispatchRules

import java.util.UUID

class R2dbcWaveDispatchAssignmentRepositorySuite extends PostgresContainerSuite:

  private given org.apache.pekko.actor.typed.ActorSystem[?] =
    system
  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private lazy val repo =
    R2dbcWaveDispatchAssignmentRepository(connectionFactory)

  private val dock01Id =
    LocationId(UUID.fromString("019e0000-0003-7000-8000-000000000020"))

  describe("R2dbcWaveDispatchAssignmentRepository"):
    describe("findActiveByDock"):
      it("returns an empty list when no active assignments exist"):
        val result =
          repo.findActiveByDock(dock01Id).futureValue
        assert(result.isEmpty)

      it("returns empty for a nonexistent dock"):
        val result =
          repo.findActiveByDock(LocationId()).futureValue
        assert(result.isEmpty)

    describe("reserveForWave"):
      it("succeeds for an empty assignment list"):
        val result = repo
          .reserveForWave(
            WaveId(),
            Nil,
            WaveDispatchRules()
          )
          .futureValue
        assert(result.isRight)
