package neon.handlingunit

/** Read-side projection schema for the handling unit module: the single owner of the table and
  * column names used by the app-side `HandlingUnitProjectionHandler`. The module's
  * [[PekkoHandlingUnitRepository]] does not query this table; the schema lives here so the module
  * owns its read-model vocabulary. DDL: app/src/main/resources/db/V5__read_side_projections.sql.
  */
object HandlingUnitProjectionSchema:

  /** Handling units for batch lookup by id. */
  object HandlingUnitLookup:
    val Table = "handling_unit_lookup"
    val HandlingUnitId = "handling_unit_id"
    val PackagingLevel = "packaging_level"
    val CurrentLocation = "current_location"
    val State = "state"

    val Upsert =
      s"""INSERT INTO $Table
         |  ($HandlingUnitId, $PackagingLevel, $CurrentLocation, $State)
         |VALUES ($$1, $$2, $$3, $$4)
         |ON CONFLICT ($HandlingUnitId) DO UPDATE
         |  SET $State = $$4, $CurrentLocation = $$3""".stripMargin

    val UpdateStateAndCurrentLocation =
      s"UPDATE $Table SET $State = $$1, $CurrentLocation = $$2 WHERE $HandlingUnitId = $$3"
