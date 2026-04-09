package neon.app

import com.typesafe.config.Config
import org.flywaydb.core.Flyway

/** Runs Flyway database migrations at startup using the same connection parameters as the R2DBC
  * persistence layer.
  */
object FlywayMigration:

  def migrate(config: Config): Unit =
    val c = config.getConfig(
      "pekko.persistence.r2dbc.connection-factory"
    )
    val url =
      s"jdbc:postgresql://${c.getString("host")}:${c.getInt("port")}/${c.getString("database")}"

    Flyway
      .configure()
      .dataSource(url, c.getString("user"), c.getString("password"))
      .locations("classpath:db")
      .load()
      .migrate()
