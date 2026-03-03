package neon.inventory

import neon.common.{InventoryId, LocationId, Lot, PackagingLevel, SkuId}

import java.time.Instant

sealed trait InventoryEvent:
  def inventoryId: InventoryId
  def locationId: LocationId
  def skuId: SkuId
  def occurredAt: Instant

object InventoryEvent:
  case class InventoryCreated(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      packagingLevel: PackagingLevel,
      lot: Option[Lot],
      onHand: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  case class InventoryReserved(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityReserved: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  case class InventoryReleased(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityReleased: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  case class InventoryConsumed(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      lot: Option[Lot],
      quantityConsumed: Int,
      occurredAt: Instant
  ) extends InventoryEvent

  case class LotCorrected(
      inventoryId: InventoryId,
      locationId: LocationId,
      skuId: SkuId,
      previousLot: Option[Lot],
      newLot: Option[Lot],
      occurredAt: Instant
  ) extends InventoryEvent
