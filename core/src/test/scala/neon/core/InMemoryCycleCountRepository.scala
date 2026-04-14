package neon.core

import neon.common.CycleCountId
import neon.cyclecount.{CycleCount, CycleCountEvent, CycleCountRepository}

import scala.collection.mutable

class InMemoryCycleCountRepository extends CycleCountRepository:
  val store: mutable.Map[CycleCountId, CycleCount] = mutable.Map.empty
  val events: mutable.ListBuffer[CycleCountEvent] = mutable.ListBuffer.empty
  def findById(id: CycleCountId): Option[CycleCount] = store.get(id)
  def save(cycleCount: CycleCount, event: CycleCountEvent): Unit =
    store(cycleCount.id) = cycleCount
    events += event
