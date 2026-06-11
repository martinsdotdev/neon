package neon.app.http

import io.circe.{Decoder, Encoder}
import neon.app.auth.{AuthDirectives, AuthenticationService}
import neon.common.{
  ContainerId,
  GoodsReceiptId,
  InboundDeliveryId,
  Lot,
  LotAttributes,
  PackagingLevel,
  Permission,
  SkuId
}
import neon.core.AsyncInboundDeliveryService
import neon.goodsreceipt.ReceivedLine
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

import CirceSupport.given
import ProblemMapper.completeProblem

object InboundRoutes:

  case class CreateDeliveryRequest(
      skuId: String,
      packagingLevel: String,
      lot: Option[String],
      expectedQuantity: Int
  ) derives Decoder

  case class ReceiveQuantityRequest(
      quantity: Int,
      rejectedQuantity: Int
  ) derives Decoder

  case class CreateReceiptRequest(
      inboundDeliveryId: String
  ) derives Decoder

  case class RecordLineRequest(
      skuId: String,
      quantity: Int,
      packagingLevel: String,
      lot: Option[String],
      targetContainerId: Option[String]
  ) derives Decoder

  case class DeliveryResponse(
      status: String,
      deliveryId: String
  ) derives Encoder.AsObject

  case class ReceiptResponse(
      status: String,
      receiptId: String
  ) derives Encoder.AsObject

  def apply(
      inboundDeliveryService: AsyncInboundDeliveryService,
      authService: AuthenticationService
  )(using ExecutionContext): Route =
    pathPrefix("inbound"):
      AuthDirectives.requirePermission(
        Permission.InboundManage,
        authService
      ): _ =>
        concat(
          pathPrefix("deliveries"):
            concat(
              pathEnd:
                post:
                  entity(as[CreateDeliveryRequest]): request =>
                    val skuId =
                      SkuId(UUID.fromString(request.skuId))
                    val packagingLevel = PackagingLevel.valueOf(
                      request.packagingLevel
                    )
                    val lotAttributes = LotAttributes(
                      lot = request.lot.map(Lot(_))
                    )
                    onSuccess(
                      inboundDeliveryService.createDelivery(
                        skuId = skuId,
                        packagingLevel = packagingLevel,
                        lotAttributes = lotAttributes,
                        expectedQuantity = request.expectedQuantity,
                        at = Instant.now()
                      )
                    ):
                      case Right(result) =>
                        complete(
                          DeliveryResponse(
                            status = "created",
                            deliveryId = result.delivery.id.value.toString
                          )
                        )
                      case Left(error) =>
                        completeProblem(error)
              ,
              path(Segment / "receive"): idStr =>
                post:
                  entity(as[ReceiveQuantityRequest]): request =>
                    val id = InboundDeliveryId(
                      UUID.fromString(idStr)
                    )
                    onSuccess(
                      inboundDeliveryService.receiveQuantity(
                        id = id,
                        quantity = request.quantity,
                        rejectedQuantity = request.rejectedQuantity,
                        at = Instant.now()
                      )
                    ):
                      case Right(result) =>
                        complete(
                          DeliveryResponse(
                            status = "received",
                            deliveryId = result.delivery.id.value.toString
                          )
                        )
                      case Left(error) =>
                        completeProblem(error)
            )
          ,
          pathPrefix("receipts"):
            concat(
              pathEnd:
                post:
                  entity(as[CreateReceiptRequest]): request =>
                    val inboundDeliveryId = InboundDeliveryId(
                      UUID.fromString(request.inboundDeliveryId)
                    )
                    onSuccess(
                      inboundDeliveryService.createReceipt(
                        inboundDeliveryId,
                        Instant.now()
                      )
                    ):
                      case Right(result) =>
                        complete(
                          ReceiptResponse(
                            status = "created",
                            receiptId = result.receipt.id.value.toString
                          )
                        )
                      case Left(error) =>
                        completeProblem(error)
              ,
              path(Segment / "record-line"): idStr =>
                post:
                  entity(as[RecordLineRequest]): request =>
                    val receiptId = GoodsReceiptId(
                      UUID.fromString(idStr)
                    )
                    val line = ReceivedLine(
                      skuId = SkuId(UUID.fromString(request.skuId)),
                      quantity = request.quantity,
                      packagingLevel = PackagingLevel.valueOf(
                        request.packagingLevel
                      ),
                      lotAttributes = LotAttributes(
                        lot = request.lot.map(Lot(_))
                      ),
                      targetContainerId =
                        request.targetContainerId.map(id => ContainerId(UUID.fromString(id)))
                    )
                    onSuccess(
                      inboundDeliveryService.recordLine(
                        receiptId,
                        line,
                        Instant.now()
                      )
                    ):
                      case Right(result) =>
                        complete(
                          ReceiptResponse(
                            status = "line-recorded",
                            receiptId = result.receipt.id.value.toString
                          )
                        )
                      case Left(error) =>
                        completeProblem(error)
              ,
              path(Segment / "confirm"): idStr =>
                post:
                  val receiptId = GoodsReceiptId(
                    UUID.fromString(idStr)
                  )
                  onSuccess(
                    inboundDeliveryService.confirmReceipt(
                      receiptId,
                      Instant.now()
                    )
                  ):
                    case Right(result) =>
                      complete(
                        ReceiptResponse(
                          status = "confirmed",
                          receiptId = result.receipt.id.value.toString
                        )
                      )
                    case Left(error) =>
                      completeProblem(error)
            )
        )
