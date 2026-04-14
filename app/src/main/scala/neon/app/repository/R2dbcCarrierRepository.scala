package neon.app.repository

import io.r2dbc.spi.{ConnectionFactory, Row}
import neon.carrier.{AsyncCarrierRepository, Carrier}
import neon.common.CarrierId

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncCarrierRepository]]. */
class R2dbcCarrierRepository(connectionFactory: ConnectionFactory)(using
    system: org.apache.pekko.actor.typed.ActorSystem[?],
    ec: ExecutionContext
) extends AsyncCarrierRepository:

  def findById(id: CarrierId): Future[Option[Carrier]] =
    R2dbcHelper
      .queryOne(
        connectionFactory,
        "SELECT id, code, name, active FROM carrier WHERE id = $1",
        id.value
      )(mapRow)

  private def mapRow(row: Row): Carrier =
    Carrier(
      id = CarrierId(row.get("id", classOf[UUID])),
      code = row.get("code", classOf[String]),
      name = row.get("name", classOf[String]),
      active = row.get("active", classOf[java.lang.Boolean]).booleanValue
    )
