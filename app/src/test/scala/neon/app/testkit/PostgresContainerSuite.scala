package neon.app.testkit

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.cluster.MemberStatus
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.cluster.typed.{Cluster, Join}
import org.apache.pekko.persistence.query.TimestampOffset
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.persistence.r2dbc.ConnectionFactoryProvider
import org.apache.pekko.projection.r2dbc.scaladsl.R2dbcSession
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import reactor.core.publisher.Mono
import org.flywaydb.core.Flyway
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.testcontainers.containers.PostgreSQLContainer as JavaPostgreSQLContainer

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

/** Companion object that starts a PostgreSQL Testcontainer and runs Flyway migrations before any
  * test class body executes. This ensures the container is available when ScalaTestWithActorTestKit
  * reads the config at construction time.
  */
object PostgresContainerSuite:

  private val container: JavaPostgreSQLContainer[?] =
    val c = new JavaPostgreSQLContainer("postgres:16-alpine")
    c.withDatabaseName("neon_wes_test")
    c.withUsername("test")
    c.withPassword("test")
    c.start()

    Flyway
      .configure()
      .dataSource(c.getJdbcUrl, c.getUsername, c.getPassword)
      .locations("classpath:db")
      .load()
      .migrate()

    c

  val testConfig: Config = ConfigFactory
    .parseString(s"""
      pekko.actor {
        provider = cluster
        allow-java-serialization = on
        serialization-bindings {
          "neon.common.serialization.CborSerializable" = jackson-cbor
        }
      }
      pekko.remote.artery.canonical.port = 0
      pekko.cluster.seed-nodes = []

      pekko.persistence {
        journal.plugin = "pekko.persistence.r2dbc.journal"
        snapshot-store.plugin = "pekko.persistence.r2dbc.snapshot"
        state.plugin = "pekko.persistence.r2dbc.state"

        r2dbc {
          dialect = postgres
          connection-factory {
            driver = "postgres"
            host = "${container.getHost}"
            port = ${container.getMappedPort(5432)}
            database = "${container.getDatabaseName}"
            user = "${container.getUsername}"
            password = "${container.getPassword}"
            initial-size = 2
            max-size = 10
          }
        }
      }
    """)
    .resolve()

/** Base trait for integration tests that require a real PostgreSQL database. Provides a running
  * Testcontainer, Flyway-migrated schema, R2DBC ConnectionFactory, and a single-node Pekko cluster.
  */
abstract class PostgresContainerSuite
    extends ScalaTestWithActorTestKit(PostgresContainerSuite.testConfig)
    with AnyFunSpecLike
    with ScalaFutures
    with Eventually
    with BeforeAndAfterEach:

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = Span(10, Seconds),
      interval = Span(100, Millis)
    )

  protected given org.apache.pekko.util.Timeout = 5.seconds
  protected given Materializer =
    SystemMaterializer(system).materializer

  private val cluster = Cluster(system)

  cluster.manager ! Join(cluster.selfMember.address)
  eventually {
    assert(cluster.selfMember.status == MemberStatus.Up)
  }

  protected lazy val connectionFactory =
    ConnectionFactoryProvider(system)
      .connectionFactoryFor(
        "pekko.persistence.r2dbc.connection-factory"
      )

  /** Executes a SQL statement against the test database. */
  protected def executeSql(sql: String): Unit =
    val future = Source
      .fromPublisher(connectionFactory.create())
      .runWith(Sink.head)
      .flatMap { connection =>
        Source
          .fromPublisher(
            connection.createStatement(sql).execute()
          )
          .runWith(Sink.head)
          .flatMap { _ =>
            Source
              .fromPublisher(connection.close())
              .runWith(Sink.ignore)
          }(using system.executionContext)
      }(using system.executionContext)
    Await.result(future, 10.seconds)

  /** Acquires an R2DBC connection, creates an R2dbcSession, runs
    * the given block, then closes the connection.
    */
  protected def withSession(
      f: R2dbcSession => Unit
  ): Unit =
    val connection =
      Mono.from(connectionFactory.create()).block()
    try
      val session = new R2dbcSession(connection)(using
        system.executionContext,
        system
      )
      f(session)
    finally Mono.from(connection.close()).block()

  /** Creates an EventEnvelope for projection handler tests. */
  protected def envelope[E](
      event: E,
      persistenceId: String,
      entityType: String
  ): EventEnvelope[E] =
    new EventEnvelope[E](
      offset = TimestampOffset.Zero,
      persistenceId = persistenceId,
      sequenceNr = 1L,
      eventOption = Some(event),
      timestamp = System.currentTimeMillis(),
      eventMetadata = None,
      entityType = entityType,
      slice = 0
    )
  /** Queries a single integer count from the test database. */
  protected def queryCount(sql: String): Long =
    val future = Source
      .fromPublisher(connectionFactory.create())
      .runWith(Sink.head)
      .flatMap { connection =>
        Source
          .fromPublisher(
            connection.createStatement(sql).execute()
          )
          .flatMapConcat { result =>
            Source.fromPublisher(
              result.map((row, _) => row.get(0, classOf[java.lang.Long]).longValue())
            )
          }
          .runWith(Sink.head)
          .andThen { case _ =>
            Source
              .fromPublisher(connection.close())
              .runWith(Sink.ignore)
          }(using system.executionContext)
      }(using system.executionContext)
    Await.result(future, 10.seconds)
