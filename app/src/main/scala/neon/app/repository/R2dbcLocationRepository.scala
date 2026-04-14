package neon.app.repository

import io.r2dbc.spi.{ConnectionFactory, Row}
import neon.common.{LocationId, ZoneId}
import neon.location.{AsyncLocationRepository, Location, LocationType}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncLocationRepository]]. */
class R2dbcLocationRepository(connectionFactory: ConnectionFactory)(using
    system: org.apache.pekko.actor.typed.ActorSystem[?],
    ec: ExecutionContext
) extends AsyncLocationRepository:

  def findById(id: LocationId): Future[Option[Location]] =
    R2dbcHelper
      .queryOne(
        connectionFactory,
        "SELECT id, code, zone_id, type, picking_sequence FROM location WHERE id = $1",
        id.value
      )(mapRow)

  private def mapRow(row: Row): Location =
    Location(
      id = LocationId(row.get("id", classOf[UUID])),
      code = row.get("code", classOf[String]),
      zoneId = Option(row.get("zone_id", classOf[UUID])).map(ZoneId(_)),
      locationType = LocationType.valueOf(row.get("type", classOf[String])),
      pickingSequence = Option(row.get("picking_sequence", classOf[java.lang.Integer]))
        .map(_.intValue)
    )
