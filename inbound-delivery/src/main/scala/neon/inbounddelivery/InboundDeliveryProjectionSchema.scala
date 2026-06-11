package neon.inbounddelivery

/** Read-side projection schema for the inbound delivery module: the single owner of the table and
  * column names used by the app-side `InboundDeliveryProjectionHandler`. The module's Pekko
  * repository does not query this table; the schema lives here so the module owns its read-model
  * vocabulary. DDL: app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object InboundDeliveryProjectionSchema:

  /** Inbound deliveries indexed by state. */
  object InboundDeliveryByState:
    val Table = "inbound_delivery_by_state"
    val InboundDeliveryId = "inbound_delivery_id"
    val SkuId = "sku_id"
    val ExpectedQuantity = "expected_quantity"
    val ReceivedQuantity = "received_quantity"
    val RejectedQuantity = "rejected_quantity"
    val State = "state"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($InboundDeliveryId, $SkuId, $ExpectedQuantity,
         |   $ReceivedQuantity, $RejectedQuantity, $State)
         |VALUES ($$1, $$2, $$3, 0, 0, $$4)
         |ON CONFLICT ($InboundDeliveryId) DO UPDATE SET $State = $$4""".stripMargin

    val AddReceivedAndRejectedQuantities =
      s"""UPDATE $Table
         |SET $ReceivedQuantity = $ReceivedQuantity + $$1,
         |    $RejectedQuantity = $RejectedQuantity + $$2
         |WHERE $InboundDeliveryId = $$3""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $InboundDeliveryId = $$2"
