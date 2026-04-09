package neon.app.projection

import neon.app.testkit.PostgresContainerSuite
import neon.common.*
import neon.handlingunit.{HandlingUnit, HandlingUnitActor, HandlingUnitEvent}

import java.time.Instant

class HandlingUnitProjectionHandlerSuite
    extends PostgresContainerSuite:

  private given scala.concurrent.ExecutionContext =
    system.executionContext

  private val handler = HandlingUnitProjectionHandler()

  describe("HandlingUnitProjectionHandler"):

    it("inserts into handling_unit_lookup on Initialized"):
      val handlingUnitId = HandlingUnitId()
      val locationId = LocationId()
      val handlingUnit = HandlingUnit.PickCreated(
        id = handlingUnitId,
        packagingLevel = PackagingLevel.Case,
        currentLocation = locationId
      )

      val event =
        HandlingUnitActor.Initialized(handlingUnit)

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              event,
              s"HandlingUnit|${handlingUnitId.value}",
              "HandlingUnit"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM handling_unit_lookup " +
          "WHERE handling_unit_id = " +
          s"'${handlingUnitId.value}'"
      )
      assert(count == 1L)

    it(
      "updates state and location " +
        "on HandlingUnitMovedToBuffer"
    ):
      val handlingUnitId = HandlingUnitId()
      val locationId = LocationId()
      val bufferLocation = LocationId()

      val handlingUnit = HandlingUnit.PickCreated(
        id = handlingUnitId,
        packagingLevel = PackagingLevel.Pallet,
        currentLocation = locationId
      )

      val initialized =
        HandlingUnitActor.Initialized(handlingUnit)
      val movedToBuffer = HandlingUnitActor.DomainEvent(
        HandlingUnitEvent.HandlingUnitMovedToBuffer(
          handlingUnitId = handlingUnitId,
          locationId = bufferLocation,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"HandlingUnit|${handlingUnitId.value}",
              "HandlingUnit"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              movedToBuffer,
              s"HandlingUnit|${handlingUnitId.value}",
              "HandlingUnit"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM handling_unit_lookup " +
          "WHERE handling_unit_id = " +
          s"'${handlingUnitId.value}' " +
          "AND state = 'InBuffer'"
      )
      assert(count == 1L)

    it("updates state to Empty on HandlingUnitEmptied"):
      val handlingUnitId = HandlingUnitId()
      val locationId = LocationId()

      val handlingUnit = HandlingUnit.PickCreated(
        id = handlingUnitId,
        packagingLevel = PackagingLevel.Case,
        currentLocation = locationId
      )

      val initialized =
        HandlingUnitActor.Initialized(handlingUnit)
      val emptied = HandlingUnitActor.DomainEvent(
        HandlingUnitEvent.HandlingUnitEmptied(
          handlingUnitId = handlingUnitId,
          occurredAt = Instant.now()
        )
      )

      withSession { session =>
        handler
          .process(
            session,
            envelope(
              initialized,
              s"HandlingUnit|${handlingUnitId.value}",
              "HandlingUnit"
            )
          )
          .futureValue
        handler
          .process(
            session,
            envelope(
              emptied,
              s"HandlingUnit|${handlingUnitId.value}",
              "HandlingUnit"
            )
          )
          .futureValue
      }

      val count = queryCount(
        "SELECT COUNT(*) FROM handling_unit_lookup " +
          "WHERE handling_unit_id = " +
          s"'${handlingUnitId.value}' " +
          "AND state = 'Empty'"
      )
      assert(count == 1L)
