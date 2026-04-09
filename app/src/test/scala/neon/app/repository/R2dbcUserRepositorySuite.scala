package neon.app.repository

import neon.app.testkit.PostgresContainerSuite
import neon.common.{Role, UserId}

import java.util.UUID

class R2dbcUserRepositorySuite extends PostgresContainerSuite:

  private lazy val repo =
    given scala.concurrent.ExecutionContext = system.executionContext
    R2dbcUserRepository(connectionFactory)

  private val adminId =
    UserId(UUID.fromString("019e0000-0001-7000-8000-000000000001"))
  private val inactiveId =
    UserId(UUID.fromString("019e0000-0001-7000-8000-000000000006"))
  private val unknownId =
    UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  describe("R2dbcUserRepository"):
    describe("findById"):
      it("should return an active user by id"):
        val result = repo.findById(adminId).futureValue
        assert(result.isDefined)
        val user = result.get
        assert(user.id == adminId)
        assert(user.login == "admin")
        assert(user.name == "Alice Admin")
        assert(user.role == Role.Admin)
        assert(user.active)
        assert(user.passwordHash.isDefined)

      it("should return an inactive user by id"):
        val result = repo.findById(inactiveId).futureValue
        assert(result.isDefined)
        val user = result.get
        assert(user.login == "inactive")
        assert(user.name == "Irene Inactive")
        assert(user.role == Role.Operator)
        assert(!user.active)

      it("should return None for an unknown id"):
        val result = repo.findById(unknownId).futureValue
        assert(result.isEmpty)

    describe("findByLogin"):
      it("should return a user by login"):
        val result = repo.findByLogin("supervisor").futureValue
        assert(result.isDefined)
        val user = result.get
        assert(user.name == "Sam Supervisor")
        assert(user.role == Role.Supervisor)

      it("should return None for an unknown login"):
        val result = repo.findByLogin("nonexistent").futureValue
        assert(result.isEmpty)
