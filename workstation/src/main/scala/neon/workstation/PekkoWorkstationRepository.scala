package neon.workstation

import neon.common.{R2dbcProjectionQueries, WorkstationId}
import io.r2dbc.spi.ConnectionFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class PekkoWorkstationRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends AsyncWorkstationRepository
    with R2dbcProjectionQueries:

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext
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
    queryProjectionIds(
      "SELECT workstation_id FROM workstation_by_type_and_state WHERE workstation_type = $1 AND state = 'Idle' LIMIT 1",
      workstationType.toString,
      "workstation_id"
    ).flatMap(ids =>
      ids.headOption match
        case None     => Future.successful(None)
        case Some(id) =>
          findById(WorkstationId(id)).map(_.collect { case idle: Workstation.Idle =>
            idle
          })
    )

  def save(
      workstation: Workstation,
      event: WorkstationEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      WorkstationActor.EntityKey,
      workstation.id.value.toString
    )
    val ensureInitialized =
      entityRef.askWithStatus(WorkstationActor.Create(workstation, _))
    ensureInitialized.flatMap { _ =>
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
              WorkstationActor.Assign(e.consolidationGroupId, e.occurredAt, _)
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
    }
