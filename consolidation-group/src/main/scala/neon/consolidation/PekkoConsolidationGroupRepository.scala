package neon.consolidationgroup

import neon.common.{ConsolidationGroupId, WaveId}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Actor-backed implementation of [[AsyncConsolidationGroupRepository]]. */
class PekkoConsolidationGroupRepository(system: ActorSystem[?])(using Timeout)
    extends AsyncConsolidationGroupRepository:

  private given ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)

  sharding.init(
    Entity(ConsolidationGroupActor.EntityKey)(ctx => ConsolidationGroupActor(ctx.entityId))
  )

  def findById(id: ConsolidationGroupId): Future[Option[ConsolidationGroup]] =
    sharding
      .entityRefFor(ConsolidationGroupActor.EntityKey, id.value.toString)
      .ask(ConsolidationGroupActor.GetState(_))

  def findByWaveId(waveId: WaveId): Future[List[ConsolidationGroup]] =
    // TODO: query consolidation_group_by_wave projection table
    Future.successful(Nil)

  def save(
      consolidationGroup: ConsolidationGroup,
      event: ConsolidationGroupEvent
  ): Future[Unit] =
    val entityRef = sharding.entityRefFor(
      ConsolidationGroupActor.EntityKey,
      consolidationGroup.id.value.toString
    )
    event match
      case e: ConsolidationGroupEvent.ConsolidationGroupCreated =>
        entityRef
          .askWithStatus(
            ConsolidationGroupActor.Create(
              consolidationGroup.asInstanceOf[ConsolidationGroup.Created],
              e,
              _
            )
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupPicked =>
        entityRef
          .askWithStatus[ConsolidationGroupActor.PickResponse](
            ConsolidationGroupActor.Pick(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation =>
        entityRef
          .askWithStatus[ConsolidationGroupActor.ReadyForWorkstationResponse](
            ConsolidationGroupActor.ReadyForWorkstation(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupAssigned =>
        entityRef
          .askWithStatus[ConsolidationGroupActor.AssignResponse](
            ConsolidationGroupActor.Assign(e.workstationId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupCompleted =>
        entityRef
          .askWithStatus[ConsolidationGroupActor.CompleteResponse](
            ConsolidationGroupActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupCancelled =>
        entityRef
          .askWithStatus[ConsolidationGroupActor.CancelResponse](
            ConsolidationGroupActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(
      entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]
  ): Future[Unit] =
    Future
      .traverse(entries)((cg, event) => save(cg, event))
      .map(_ => ())
