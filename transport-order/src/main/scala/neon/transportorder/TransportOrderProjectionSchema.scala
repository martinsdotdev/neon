package neon.transportorder

/** Read-side projection schema for the transport order module: the single owner of the table and
  * column names shared by [[PekkoTransportOrderRepository]] queries and the app-side
  * `TransportOrderProjectionHandler`. DDL: app/src/main/resources/db/V5__read_side_projections.sql.
  */
object TransportOrderProjectionSchema:

  /** Transport orders indexed by handling unit, for
    * [[AsyncTransportOrderRepository.findByHandlingUnitId]].
    */
  object TransportOrderByHandlingUnit:
    val Table = "transport_order_by_handling_unit"
    val TransportOrderId = "transport_order_id"
    val HandlingUnitId = "handling_unit_id"
    val Destination = "destination"
    val State = "state"

    val SelectTransportOrderIdsByHandlingUnitId =
      s"SELECT $TransportOrderId FROM $Table WHERE $HandlingUnitId = $$1"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($TransportOrderId, $HandlingUnitId, $Destination, $State)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($TransportOrderId) DO UPDATE SET $State = $$4""".stripMargin

    val UpdateState =
      s"UPDATE $Table SET $State = $$1 WHERE $TransportOrderId = $$2"
