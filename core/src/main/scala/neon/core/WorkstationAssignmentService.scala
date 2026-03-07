package neon.core

import neon.common.ConsolidationGroupId
import neon.consolidationgroup.{ConsolidationGroup, ConsolidationGroupEvent, ConsolidationGroupRepository}
import neon.workstation.{Workstation, WorkstationEvent, WorkstationType, WorkstationRepository}

import java.time.Instant

/** Errors that can occur during workstation assignment. */
sealed trait WorkstationAssignmentError

object WorkstationAssignmentError:
  /** The consolidation group was not found in the repository. */
  case class ConsolidationGroupNotFound(consolidationGroupId: ConsolidationGroupId) extends WorkstationAssignmentError

  /** The consolidation group is not in the [[ConsolidationGroup.ReadyForWorkstation]] state
    * required for assignment.
    */
  case class ConsolidationGroupNotReady(consolidationGroupId: ConsolidationGroupId) extends WorkstationAssignmentError

  /** No idle put-wall workstation is available for assignment. */
  case class NoWorkstationAvailable(consolidationGroupId: ConsolidationGroupId) extends WorkstationAssignmentError

/** The result of a successful workstation assignment, containing the assigned consolidation group
  * and the activated workstation.
  *
  * @param consolidationGroup
  *   the assigned consolidation group
  * @param consolidationGroupEvent
  *   the consolidation group assignment event
  * @param workstation
  *   the workstation now active
  * @param workstationEvent
  *   the workstation assignment event
  */
case class WorkstationAssignmentResult(
    consolidationGroup: ConsolidationGroup.Assigned,
    consolidationGroupEvent: ConsolidationGroupEvent.ConsolidationGroupAssigned,
    workstation: Workstation.Active,
    workstationEvent: WorkstationEvent.WorkstationAssigned
)

/** Assigns a [[ConsolidationGroup.ReadyForWorkstation]] consolidation group to an idle put-wall
  * [[Workstation]], activating the workstation and transitioning the group to
  * [[ConsolidationGroup.Assigned]].
  *
  * @param consolidationGroupRepository
  *   repository for consolidation group lookup and persistence
  * @param workstationRepository
  *   repository for workstation lookup and persistence
  */
class WorkstationAssignmentService(
    consolidationGroupRepository: ConsolidationGroupRepository,
    workstationRepository: WorkstationRepository
):
  /** Assigns a consolidation group to an available workstation.
    *
    * Finds the [[ConsolidationGroup.ReadyForWorkstation]] group, locates an idle put-wall
    * workstation, and delegates to [[WorkstationAssignmentPolicy]] for the cross-aggregate
    * transition.
    *
    * @param consolidationGroupId
    *   the consolidation group to assign
    * @param at
    *   instant of the assignment
    * @return
    *   assignment result or error
    */
  def assign(
      consolidationGroupId: ConsolidationGroupId,
      at: Instant
  ): Either[WorkstationAssignmentError, WorkstationAssignmentResult] =
    consolidationGroupRepository.findById(consolidationGroupId) match
      case None =>
        Left(WorkstationAssignmentError.ConsolidationGroupNotFound(consolidationGroupId))
      case Some(ready: ConsolidationGroup.ReadyForWorkstation) =>
        assignToWorkstation(ready, at)
      case Some(_) =>
        Left(WorkstationAssignmentError.ConsolidationGroupNotReady(consolidationGroupId))

  /** Finds an idle put-wall workstation and performs the cross-aggregate assignment via
    * [[WorkstationAssignmentPolicy]].
    */
  private def assignToWorkstation(
      ready: ConsolidationGroup.ReadyForWorkstation,
      at: Instant
  ): Either[WorkstationAssignmentError, WorkstationAssignmentResult] =
    workstationRepository.findIdleByType(WorkstationType.PutWall) match
      case None =>
        Left(WorkstationAssignmentError.NoWorkstationAvailable(ready.id))
      case Some(idle) =>
        WorkstationAssignmentPolicy(ready, idle, at) match
          case None =>
            Left(WorkstationAssignmentError.NoWorkstationAvailable(ready.id))
          case Some(
                (
                  (assignedConsolidationGroup, consolidationGroupEvent),
                  (activeWorkstation, workstationEvent)
                )
              ) =>
            consolidationGroupRepository.save(assignedConsolidationGroup, consolidationGroupEvent)
            workstationRepository.save(activeWorkstation, workstationEvent)
            Right(
              WorkstationAssignmentResult(
                consolidationGroup = assignedConsolidationGroup,
                consolidationGroupEvent = consolidationGroupEvent,
                workstation = activeWorkstation,
                workstationEvent = workstationEvent
              )
            )
