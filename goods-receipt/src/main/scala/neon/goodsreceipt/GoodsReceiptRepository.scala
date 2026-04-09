package neon.goodsreceipt

import neon.common.GoodsReceiptId

/** Port trait for [[GoodsReceipt]] aggregate persistence and queries. */
trait GoodsReceiptRepository:

  /** Finds a goods receipt by its unique identifier.
    *
    * @param id
    *   the goods receipt identifier
    * @return
    *   the goods receipt if it exists, [[None]] otherwise
    */
  def findById(id: GoodsReceiptId): Option[GoodsReceipt]

  /** Persists a goods receipt state together with the event that caused the transition.
    *
    * @param receipt
    *   the goods receipt state to persist
    * @param event
    *   the event produced by the transition
    */
  def save(receipt: GoodsReceipt, event: GoodsReceiptEvent): Unit
