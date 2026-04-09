package neon.handlingunit

import neon.common.{ContainerId, HandlingUnitStockId}

import scala.concurrent.Future

/** Async port trait for [[HandlingUnitStock]] aggregate persistence and queries. */
trait AsyncHandlingUnitStockRepository:
  def findById(id: HandlingUnitStockId): Future[Option[HandlingUnitStock]]
  def findByContainer(containerId: ContainerId): Future[List[HandlingUnitStock]]
  def save(handlingUnitStock: HandlingUnitStock, event: HandlingUnitStockEvent): Future[Unit]
