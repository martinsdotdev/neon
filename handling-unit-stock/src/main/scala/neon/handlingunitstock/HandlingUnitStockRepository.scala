package neon.handlingunitstock

import neon.common.{ContainerId, HandlingUnitStockId}

/** Port trait for [[HandlingUnitStock]] aggregate persistence and queries. */
trait HandlingUnitStockRepository:

  /** Finds a handling unit stock by its unique identifier. */
  def findById(id: HandlingUnitStockId): Option[HandlingUnitStock]

  /** Finds all handling unit stocks for a given container. */
  def findByContainer(containerId: ContainerId): List[HandlingUnitStock]

  /** Persists a handling unit stock along with the event that caused the state change. */
  def save(handlingUnitStock: HandlingUnitStock, event: HandlingUnitStockEvent): Unit
