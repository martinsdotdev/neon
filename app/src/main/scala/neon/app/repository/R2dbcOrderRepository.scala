package neon.app.repository

import neon.common.{CarrierId, OrderId, PackagingLevel, Priority, SkuId}
import neon.order.{AsyncOrderRepository, Order, OrderLine}
import io.circe.parser.decode as jsonDecode
import io.circe.{Decoder, HCursor}
import io.r2dbc.spi.{ConnectionFactory, Row}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** R2DBC-backed implementation of [[AsyncOrderRepository]]. */
class R2dbcOrderRepository(connectionFactory: ConnectionFactory)(using
    ExecutionContext
) extends AsyncOrderRepository:

  def findById(id: OrderId): Future[Option[Order]] =
    R2dbcHelper
      .queryOne(
        connectionFactory,
        "SELECT id, priority, carrier_id, lines FROM orders WHERE id = $1",
        id.value
      )(mapRow)

  def findByIds(ids: List[OrderId]): Future[List[Order]] =
    if ids.isEmpty then Future.successful(Nil)
    else
      val placeholders = ids.indices.map(i => s"$$${i + 1}").mkString(", ")
      R2dbcHelper
        .queryList(
          connectionFactory,
          s"SELECT id, priority, carrier_id, lines FROM orders WHERE id IN ($placeholders)",
          ids.map(_.value)*
        )(mapRow)

  private given Decoder[OrderLine] = (c: HCursor) =>
    for
      skuId <- c.get[String]("skuId").map(s => SkuId(UUID.fromString(s)))
      pl <- c.get[String]("packagingLevel").map(PackagingLevel.valueOf)
      qty <- c.get[Int]("quantity")
    yield OrderLine(skuId, pl, qty)

  private def mapRow(row: Row): Order =
    val linesJson = row.get("lines", classOf[String])
    val lines = jsonDecode[List[OrderLine]](linesJson).getOrElse(Nil)
    Order(
      id = OrderId(row.get("id", classOf[UUID])),
      priority = Priority.valueOf(row.get("priority", classOf[String])),
      lines = lines,
      carrierId = Option(row.get("carrier_id", classOf[UUID])).map(CarrierId(_))
    )
