package neon.consolidationgroup

import io.r2dbc.spi.ConnectionFactory
import neon.common.entity.PekkoEntityRepository
import neon.common.{ConsolidationGroupId, R2dbcProjectionQueries, WaveId}
import neon.consolidationgroup.ConsolidationGroupProjectionSchema.ConsolidationGroupByWave
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import scala.concurrent.Future

class PekkoConsolidationGroupRepository(
    actorSystem: ActorSystem[?],
    val connectionFactory: ConnectionFactory
)(using Timeout)
    extends PekkoEntityRepository[ConsolidationGroupActor.Command, ConsolidationGroup](
      actorSystem = actorSystem,
      entityKey = ConsolidationGroupActor.EntityKey,
      behaviorFactory = ConsolidationGroupActor.apply,
      getState = ConsolidationGroupActor.GetState.apply
    )
    with AsyncConsolidationGroupRepository
    with R2dbcProjectionQueries:

  def findById(id: ConsolidationGroupId): Future[Option[ConsolidationGroup]] =
    findByEntityId(id.value.toString)

  def findByWaveId(waveId: WaveId): Future[List[ConsolidationGroup]] =
    queryProjectionIds(
      sql = ConsolidationGroupByWave.SelectConsolidationGroupIdsByWaveId,
      param = waveId.value,
      idColumn = ConsolidationGroupByWave.ConsolidationGroupId
    ).flatMap(ids =>
      Future
        .sequence(ids.map(id => findById(ConsolidationGroupId(id))))
        .map(_.flatten)
    )

  def save(
      consolidationGroup: ConsolidationGroup,
      event: ConsolidationGroupEvent
  ): Future[Unit] =
    val ref = entityRef(consolidationGroup.id.value.toString)
    event match
      case e: ConsolidationGroupEvent.ConsolidationGroupCreated =>
        ref
          .askWithStatus(
            ConsolidationGroupActor.Create(
              consolidationGroup.asInstanceOf[ConsolidationGroup.Created],
              e,
              _
            )
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupPicked =>
        ref
          .askWithStatus[ConsolidationGroupActor.PickResponse](
            ConsolidationGroupActor.Pick(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupReadyForWorkstation =>
        ref
          .askWithStatus[ConsolidationGroupActor.ReadyForWorkstationResponse](
            ConsolidationGroupActor.ReadyForWorkstation(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupAssigned =>
        ref
          .askWithStatus[ConsolidationGroupActor.AssignResponse](
            ConsolidationGroupActor.Assign(e.workstationId, e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupCompleted =>
        ref
          .askWithStatus[ConsolidationGroupActor.CompleteResponse](
            ConsolidationGroupActor.Complete(e.occurredAt, _)
          )
          .map(_ => ())
      case e: ConsolidationGroupEvent.ConsolidationGroupCancelled =>
        ref
          .askWithStatus[ConsolidationGroupActor.CancelResponse](
            ConsolidationGroupActor.Cancel(e.occurredAt, _)
          )
          .map(_ => ())

  def saveAll(
      entries: List[(ConsolidationGroup, ConsolidationGroupEvent)]
  ): Future[Unit] =
    sequenceSaves(entries)(save)
