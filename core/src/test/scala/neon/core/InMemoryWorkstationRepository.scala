package neon.core

import neon.common.WorkstationId
import neon.workstation.{Workstation, WorkstationEvent, WorkstationRepository, WorkstationType}

import scala.collection.mutable

class InMemoryWorkstationRepository extends WorkstationRepository:
  val store: mutable.Map[WorkstationId, Workstation] = mutable.Map.empty
  val events: mutable.ListBuffer[WorkstationEvent] = mutable.ListBuffer.empty
  def findById(id: WorkstationId): Option[Workstation] = store.get(id)
  def findIdleByType(workstationType: WorkstationType): Option[Workstation.Idle] =
    store.values.collectFirst {
      case idle: Workstation.Idle if idle.workstationType == workstationType =>
        idle
    }
  def save(workstation: Workstation, event: WorkstationEvent): Unit =
    store(workstation.id) = workstation
    events += event
