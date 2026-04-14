# Appendix E: Configuration Reference

Neon WES configuration lives in `app/src/main/resources/application.conf`. The file uses HOCON format (Typesafe Config). Environment variable overrides follow the `${?ENV_VAR}` pattern, allowing the same config file to serve development defaults and production deployments.

---

## Pekko Actor System

```hocon
pekko.actor {
  provider = cluster
  allow-java-serialization = off
}
```

| Key | Value | Notes |
|---|---|---|
| `provider` | `cluster` | Enables Pekko Cluster for distributed sharding |
| `allow-java-serialization` | `off` | Java serialization is explicitly disabled for security and performance |

---

## Serialization (Jackson CBOR)

```hocon
pekko.actor {
  serializers {
    jackson-cbor = "org.apache.pekko.serialization.jackson.JacksonCborSerializer"
  }
  serialization-bindings {
    "neon.common.serialization.CborSerializable" = jackson-cbor
  }
}

pekko.serialization.jackson {
  jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"
}
```

| Key | Value | Notes |
|---|---|---|
| `serializers.jackson-cbor` | `JacksonCborSerializer` | Binary CBOR format for compact, fast serialization |
| `serialization-bindings` | `CborSerializable` marker trait | All commands, responses, events, and state wrappers implement this trait |
| `jackson-modules` | `DefaultScalaModule` | Required for Scala case class serialization |

Aggregate sealed traits (e.g., `Wave`, `Task`) require `@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)` for polymorphic snapshot deserialization.

---

## Pekko Remote (Artery)

```hocon
pekko.remote.artery {
  canonical.hostname = "127.0.0.1"
  canonical.port = 25520
}
```

| Key | Default | Notes |
|---|---|---|
| `canonical.hostname` | `127.0.0.1` | Bind address for cluster communication |
| `canonical.port` | `25520` | TCP port for Artery remoting |

---

## Pekko Cluster

```hocon
pekko.cluster {
  seed-nodes = ["pekko://neon-wes@127.0.0.1:25520"]
  downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
  sharding.number-of-shards = 100
}
```

| Key | Value | Notes |
|---|---|---|
| `seed-nodes` | Single-node seed | List of initial contact points for cluster formation |
| `downing-provider-class` | `SplitBrainResolverProvider` | Automatic split-brain resolution for production safety |
| `sharding.number-of-shards` | `100` | Total number of shards across the cluster; fixed after deployment |

---

## Pekko Persistence (R2DBC)

```hocon
pekko.persistence {
  journal.plugin = "pekko.persistence.r2dbc.journal"
  snapshot-store.plugin = "pekko.persistence.r2dbc.snapshot"
  state.plugin = "pekko.persistence.r2dbc.state"
}
```

| Key | Value | Notes |
|---|---|---|
| `journal.plugin` | `pekko.persistence.r2dbc.journal` | Event journal stored in PostgreSQL via R2DBC |
| `snapshot-store.plugin` | `pekko.persistence.r2dbc.snapshot` | Snapshot store for faster recovery |
| `state.plugin` | `pekko.persistence.r2dbc.state` | Durable state store |

### R2DBC Connection Pool

```hocon
pekko.persistence.r2dbc.connection-factory {
  max-size = 200
  acquire-timeout = 15 seconds
  acquire-retry = 5
  max-idle-time = 30 seconds
  max-life-time = 5 minutes
  host = "localhost"
  port = 5432
  database = "neon_wes"
  user = "neon"
  password = "neon"
}
```

| Key | Default | Env Override | Notes |
|---|---|---|---|
| `host` | `localhost` | `NEON_DB_HOST` | PostgreSQL host |
| `port` | `5432` | `NEON_DB_PORT` | PostgreSQL port |
| `database` | `neon_wes` | `NEON_DB_NAME` | Database name |
| `user` | `neon` | `NEON_DB_USER` | Database user |
| `password` | `neon` | `NEON_DB_PASSWORD` | Database password |
| `max-size` | `200` | | Maximum connections in the pool |
| `acquire-timeout` | `15 seconds` | | How long to wait for a connection |
| `acquire-retry` | `5` | | Number of acquire retries |
| `max-idle-time` | `30 seconds` | | Idle connection timeout |
| `max-life-time` | `5 minutes` | | Maximum connection lifetime |

### Projection Connection Pool

```hocon
pekko.persistence.r2dbc.projection-connection-factory =
  ${pekko.persistence.r2dbc.connection-factory} {
    max-size = 100
  }
```

A separate pool for CQRS projections prevents projection polling from starving persistence operations. It inherits all settings from the main pool but uses a smaller `max-size` of 100.

---

## Pekko Projection (R2DBC)

```hocon
pekko.projection.r2dbc {
  use-connection-factory = "pekko.persistence.r2dbc.projection-connection-factory"
}
```

Projections use the dedicated projection connection pool for offset storage and read-side queries.

---

## Retention and Snapshots

Configured per actor (not in `application.conf`), applied uniformly:

```scala
EventSourcedBehavior
  .withEnforcedReplies[Command, Event, State](...)
  .withRetention(
    RetentionCriteria.snapshotEvery(100, 2)
  )
```

| Parameter | Value | Notes |
|---|---|---|
| Snapshot interval | every 100 events | A snapshot is taken after every 100 persisted events |
| Snapshots to keep | 2 | Only the 2 most recent snapshots are retained; older ones are deleted |

On recovery, the actor loads the latest snapshot and replays only events after that sequence number.

---

## HTTP Server

```hocon
neon.http {
  host = "0.0.0.0"
  port = 8080
}
```

| Key | Default | Env Override | Notes |
|---|---|---|---|
| `host` | `0.0.0.0` | `NEON_HTTP_HOST` | Bind address for the HTTP server |
| `port` | `8080` | `NEON_HTTP_PORT` | HTTP server port |

---

## Authentication

```hocon
neon.auth {
  session-max-age = 30d
  session-renewal-threshold = 15d
  secure-cookies = true
}
```

| Key | Default | Env Override | Notes |
|---|---|---|---|
| `session-max-age` | `30d` | | Maximum session token lifetime |
| `session-renewal-threshold` | `15d` | | Sessions are renewed if older than this threshold |
| `secure-cookies` | `true` | `NEON_AUTH_SECURE_COOKIES` | Set to `false` for local development (HTTP instead of HTTPS) |

---

## Flyway Migrations

Flyway runs schema migrations at startup before any actor system interaction. It uses a blocking JDBC connection (separate from the R2DBC pool) configured from the same R2DBC connection parameters.

```scala
object FlywayMigration:
  def migrate(config: Config): Unit =
    Flyway.configure()
      .dataSource(jdbcUrl, user, password)
      .load()
      .migrate()
```

| Setting | Value | Notes |
|---|---|---|
| Migration location | `db/migration` (classpath) | Standard Flyway convention |
| Repeatable migrations | `db/R__*.sql` | Used for development seed data |
| Connection source | Same host/port/database as R2DBC | JDBC URL constructed from R2DBC config |

---

## Environment Variables Summary

| Variable | Config Path | Default |
|---|---|---|
| `NEON_DB_HOST` | `pekko.persistence.r2dbc.connection-factory.host` | `localhost` |
| `NEON_DB_PORT` | `pekko.persistence.r2dbc.connection-factory.port` | `5432` |
| `NEON_DB_NAME` | `pekko.persistence.r2dbc.connection-factory.database` | `neon_wes` |
| `NEON_DB_USER` | `pekko.persistence.r2dbc.connection-factory.user` | `neon` |
| `NEON_DB_PASSWORD` | `pekko.persistence.r2dbc.connection-factory.password` | `neon` |
| `NEON_HTTP_HOST` | `neon.http.host` | `0.0.0.0` |
| `NEON_HTTP_PORT` | `neon.http.port` | `8080` |
| `NEON_AUTH_SECURE_COOKIES` | `neon.auth.secure-cookies` | `true` |
