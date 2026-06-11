package neon.workstation

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{R2dbcProjectionQueries, WorkstationId}
import neon.workstation.WorkstationProjectionSchema.WorkstationByTypeAndState
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoWorkstationRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[WorkstationActor.Command, Workstation](
      actorSystem = actorSystem,
      entityKey = WorkstationActor.EntityKey,
      behaviorFactory = WorkstationActor.apply,
      getState = WorkstationActor.GetState.apply
    )
    with AsyncWorkstationRepository
    with R2dbcProjectionQueries:

  def findById(id: WorkstationId): Future[Option[Workstation]] =
    findByEntityId(id.value.toString)

  def findIdleByType(
      workstationType: WorkstationType
  ): Future[Option[Workstation.Idle]] =
    queryProjectionIds(
      sql = WorkstationByTypeAndState.SelectIdleWorkstationIdByType,
      param = workstationType.toString,
      idColumn = WorkstationByTypeAndState.WorkstationId
    ).flatMap(ids =>
      ids.headOption match
        case None     => Future.successful(None)
        case Some(id) =>
          findById(WorkstationId(id)).map(_.collect { case idle: Workstation.Idle =>
            idle
          })
    )

  def create(workstation: Workstation.Disabled): Future[Unit] =
    entityRef(workstation.id.value.toString)
      .askWithStatus(WorkstationActor.Create(workstation, _))
      .map(_ => ())

  def save(
      workstation: Workstation,
      event: WorkstationEvent
  ): Future[Unit] =
    val ref = entityRef(workstation.id.value.toString)
    val ensureInitialized =
      ref.askWithStatus(WorkstationActor.Create(workstation, _))
    ensureInitialized.flatMap { _ =>
      event match
        case e: WorkstationEvent.WorkstationEnabled =>
          ref
            .askWithStatus[WorkstationActor.EnableResponse](
              WorkstationActor.Enable(e.occurredAt, _)
            )
            .map(_ => ())
        case e: WorkstationEvent.WorkstationAssigned =>
          ref
            .askWithStatus[WorkstationActor.AssignResponse](
              WorkstationActor.Assign(e.assignmentId, e.occurredAt, _)
            )
            .map(_ => ())
        case e: WorkstationEvent.ModeSwitched =>
          ref
            .askWithStatus[WorkstationActor.SwitchModeResponse](
              WorkstationActor.SwitchMode(e.newMode, e.occurredAt, _)
            )
            .map(_ => ())
        case e: WorkstationEvent.WorkstationReleased =>
          ref
            .askWithStatus[WorkstationActor.ReleaseResponse](
              WorkstationActor.Release(e.occurredAt, _)
            )
            .map(_ => ())
        case e: WorkstationEvent.WorkstationDisabled =>
          ref
            .askWithStatus[WorkstationActor.DisableResponse](
              WorkstationActor.Disable(e.occurredAt, _)
            )
            .map(_ => ())
    }
