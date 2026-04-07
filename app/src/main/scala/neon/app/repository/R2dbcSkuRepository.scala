package neon.app.repository

import neon.common.SkuId
import neon.sku.{AsyncSkuRepository, Sku}
import io.r2dbc.spi.{ConnectionFactory, Row}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncSkuRepository]]. */
class R2dbcSkuRepository(connectionFactory: ConnectionFactory)(using
    ExecutionContext
) extends AsyncSkuRepository:

  def findById(id: SkuId): Future[Option[Sku]] =
    R2dbcHelper
      .queryOne(
        connectionFactory,
        "SELECT id, name, uom_hierarchy FROM sku WHERE id = $1",
        id.value
      )(mapRow)

  private def mapRow(row: Row): Sku =
    Sku(
      id = SkuId(row.get("id", classOf[UUID])),
      code = row.get("name", classOf[String]),
      description = row.get("name", classOf[String]),
      lotManaged = false
    )
