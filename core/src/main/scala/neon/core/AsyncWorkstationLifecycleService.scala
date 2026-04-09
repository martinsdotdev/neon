package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.WorkstationId
import neon.workstation.{AsyncWorkstationRepository, Workstation, WorkstationEvent, WorkstationType}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait WorkstationLifecycleError
object WorkstationLifecycleError:
  case class WorkstationNotFound(workstationId: WorkstationId) extends WorkstationLifecycleError
  case class WorkstationInWrongState(workstationId: WorkstationId) extends WorkstationLifecycleError

case class WorkstationCreateResult(workstation: Workstation.Disabled)
case class WorkstationEnableResult(
    idle: Workstation.Idle,
    event: WorkstationEvent.WorkstationEnabled
)
case class WorkstationDisableResult(
    disabled: Workstation.Disabled,
    event: WorkstationEvent.WorkstationDisabled
)

class AsyncWorkstationLifecycleService(
    workstationRepository: AsyncWorkstationRepository
)(using ExecutionContext)
    extends LazyLogging:

  def create(
      workstationType: WorkstationType,
      slotCount: Int
  ): Future[Either[WorkstationLifecycleError, WorkstationCreateResult]] =
    val id = WorkstationId()
    val disabled = Workstation.Disabled(id, workstationType, slotCount)
    workstationRepository.create(disabled).map(_ => Right(WorkstationCreateResult(disabled)))

  def enable(
      workstationId: WorkstationId,
      at: Instant
  ): Future[Either[WorkstationLifecycleError, WorkstationEnableResult]] =
    workstationRepository
      .findById(workstationId)
      .flatMap:
        case None =>
          Future.successful(Left(WorkstationLifecycleError.WorkstationNotFound(workstationId)))
        case Some(disabled: Workstation.Disabled) =>
          val (idle, event) = disabled.enable(at)
          workstationRepository
            .save(idle, event)
            .map(_ => Right(WorkstationEnableResult(idle, event)))
        case Some(_) =>
          Future.successful(Left(WorkstationLifecycleError.WorkstationInWrongState(workstationId)))

  def disable(
      workstationId: WorkstationId,
      at: Instant
  ): Future[Either[WorkstationLifecycleError, WorkstationDisableResult]] =
    workstationRepository
      .findById(workstationId)
      .flatMap:
        case None =>
          Future.successful(Left(WorkstationLifecycleError.WorkstationNotFound(workstationId)))
        case Some(idle: Workstation.Idle) =>
          val (disabled, event) = idle.disable(at)
          workstationRepository
            .save(disabled, event)
            .map(_ => Right(WorkstationDisableResult(disabled, event)))
        case Some(active: Workstation.Active) =>
          val (disabled, event) = active.disable(at)
          workstationRepository
            .save(disabled, event)
            .map(_ => Right(WorkstationDisableResult(disabled, event)))
        case Some(_) =>
          Future.successful(Left(WorkstationLifecycleError.WorkstationInWrongState(workstationId)))
