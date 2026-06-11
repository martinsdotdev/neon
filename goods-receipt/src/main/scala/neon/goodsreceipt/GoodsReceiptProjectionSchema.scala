package neon.goodsreceipt

/** Read-side projection schema for the goods receipt module: the single owner of the table and
  * column names used by the app-side `GoodsReceiptProjectionHandler`. The module's Pekko
  * repository does not query this table; the schema lives here so the module owns its read-model
  * vocabulary. DDL: app/src/main/resources/db/V7__stock_inbound_cycle_count_tables.sql.
  */
object GoodsReceiptProjectionSchema:

  /** Goods receipts indexed by inbound delivery. */
  object GoodsReceiptByDelivery:
    val Table = "goods_receipt_by_delivery"
    val GoodsReceiptId = "goods_receipt_id"
    val InboundDeliveryId = "inbound_delivery_id"
    val State = "state"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($GoodsReceiptId, $InboundDeliveryId, $State)
         |VALUES ($$1, $$2, $$3)
         |ON CONFLICT ($GoodsReceiptId) DO UPDATE SET $State = $$3""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $GoodsReceiptId = $$2"
