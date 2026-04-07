package neon.workstation

import neon.common.WorkstationId

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncWorkstationRepository]]. */
class PekkoWorkstationRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncWorkstationRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(WorkstationActor.EntityKey)(ctx => WorkstationActor(ctx.entityId))
  )

  def findById(id: WorkstationId): Future[Option[Workstation]] =
    sharding
      .entityRefFor(WorkstationActor.EntityKey, id.value.toString)
      .ask(WorkstationActor.GetState(_))

  def findIdleByType(
      workstationType: WorkstationType
  ): Future[Option[Workstation.Idle]] =
    // TODO: query workstation_by_type_and_state projection table
    Future.successful(None)

  def save(
      workstation: Workstation,
      event: WorkstationEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      WorkstationActor.EntityKey,
      workstation.id.value.toString
    )
    event match
      case e: WorkstationEvent.WorkstationEnabled =>
        entityRef
          .askWithStatus[WorkstationActor.EnableResponse](
            WorkstationActor.Enable(e.occurredAt, _)
          )
          .map(_ => ())
      case e: WorkstationEvent.WorkstationAssigned =>
        entityRef
          .askWithStatus[WorkstationActor.AssignResponse](
            WorkstationActor.Assign(
              e.consolidationGroupId,
              e.occurredAt,
              _
            )
          )
          .map(_ => ())
      case e: WorkstationEvent.WorkstationReleased =>
        entityRef
          .askWithStatus[WorkstationActor.ReleaseResponse](
            WorkstationActor.Release(e.occurredAt, _)
          )
          .map(_ => ())
      case e: WorkstationEvent.WorkstationDisabled =>
        entityRef
          .askWithStatus[WorkstationActor.DisableResponse](
            WorkstationActor.Disable(e.occurredAt, _)
          )
          .map(_ => ())
