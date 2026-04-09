package neon.core

import com.typesafe.scalalogging.LazyLogging
import neon.common.{
  CountMethod,
  CountTaskId,
  CountType,
  CycleCountId,
  SkuId,
  UserId,
  WarehouseAreaId
}
import neon.counttask.{AsyncCountTaskRepository, CountTask, CountTaskEvent}
import neon.cyclecount.{AsyncCycleCountRepository, CycleCount, CycleCountEvent}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

sealed trait CycleCountError
object CycleCountError:
  case class CycleCountNotFound(id: CycleCountId) extends CycleCountError
  case class CycleCountInWrongState(id: CycleCountId) extends CycleCountError
  case class CountTaskNotFound(id: CountTaskId) extends CycleCountError
  case class CountTaskInWrongState(id: CountTaskId) extends CycleCountError

case class CycleCountCreateResult(
    cycleCount: CycleCount.New,
    event: CycleCountEvent.CycleCountCreated
)
case class CycleCountStartResult(
    cycleCount: CycleCount.InProgress,
    event: CycleCountEvent.CycleCountStarted
)
case class CountTaskAssignResult(
    countTask: CountTask.Assigned,
    event: CountTaskEvent.CountTaskAssigned
)
case class CountTaskRecordResult(
    countTask: CountTask.Recorded,
    event: CountTaskEvent.CountTaskRecorded
)

class AsyncCycleCountService(
    cycleCountRepository: AsyncCycleCountRepository,
    countTaskRepository: AsyncCountTaskRepository
)(using ExecutionContext)
    extends LazyLogging:

  def create(
      warehouseAreaId: WarehouseAreaId,
      skuIds: List[SkuId],
      countType: CountType,
      countMethod: CountMethod,
      at: Instant
  ): Future[Either[CycleCountError, CycleCountCreateResult]] =
    val id = CycleCountId()
    val cycleCount =
      CycleCount.New(id, warehouseAreaId, skuIds, countType, countMethod)
    val event = CycleCountEvent.CycleCountCreated(
      id,
      warehouseAreaId,
      skuIds,
      countType,
      countMethod,
      at
    )
    cycleCountRepository
      .save(cycleCount, event)
      .map(_ => Right(CycleCountCreateResult(cycleCount, event)))

  def start(
      id: CycleCountId,
      at: Instant
  ): Future[Either[CycleCountError, CycleCountStartResult]] =
    cycleCountRepository
      .findById(id)
      .flatMap:
        case None =>
          Future.successful(Left(CycleCountError.CycleCountNotFound(id)))
        case Some(newCc: CycleCount.New) =>
          val (inProgress, event) = newCc.start(at)
          cycleCountRepository
            .save(inProgress, event)
            .map(_ => Right(CycleCountStartResult(inProgress, event)))
        case Some(_) =>
          Future.successful(
            Left(CycleCountError.CycleCountInWrongState(id))
          )

  def assignCountTask(
      countTaskId: CountTaskId,
      userId: UserId,
      at: Instant
  ): Future[Either[CycleCountError, CountTaskAssignResult]] =
    countTaskRepository
      .findById(countTaskId)
      .flatMap:
        case None =>
          Future.successful(
            Left(CycleCountError.CountTaskNotFound(countTaskId))
          )
        case Some(pending: CountTask.Pending) =>
          val (assigned, event) = pending.assign(userId, at)
          countTaskRepository
            .save(assigned, event)
            .map(_ => Right(CountTaskAssignResult(assigned, event)))
        case Some(_) =>
          Future.successful(
            Left(CycleCountError.CountTaskInWrongState(countTaskId))
          )

  def recordCount(
      countTaskId: CountTaskId,
      actualQuantity: Int,
      at: Instant
  ): Future[Either[CycleCountError, CountTaskRecordResult]] =
    countTaskRepository
      .findById(countTaskId)
      .flatMap:
        case None =>
          Future.successful(
            Left(CycleCountError.CountTaskNotFound(countTaskId))
          )
        case Some(assigned: CountTask.Assigned) =>
          val (recorded, event) = assigned.record(actualQuantity, at)
          countTaskRepository
            .save(recorded, event)
            .map(_ => Right(CountTaskRecordResult(recorded, event)))
        case Some(_) =>
          Future.successful(
            Left(CycleCountError.CountTaskInWrongState(countTaskId))
          )
