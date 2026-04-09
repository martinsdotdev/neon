package neon.app.testkit

class PostgresContainerSmokeTestSuite extends PostgresContainerSuite:

  describe("PostgresContainerSuite"):
    it("connects to PostgreSQL and verifies Flyway migrations ran"):
      val count =
        queryCount(
          "SELECT count(*) FROM information_schema.tables WHERE table_name = 'event_journal'"
        )
      assert(count == 1, "event_journal table should exist")

    it("verifies seed data was loaded"):
      val userCount =
        queryCount("SELECT count(*) FROM users")
      assert(userCount > 0, "seed data should include users")
