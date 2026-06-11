package neon.common.entity

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityRef,
  EntityTypeKey
}
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

/** Base class for actor-backed repositories: initializes cluster sharding for the entity, exposes
  * entity refs, and provides the uniform state query and save fan-out. The `system` and `ec`
  * givens also satisfy the abstract members of `R2dbcProjectionQueries` for repositories that mix
  * it in for read-side queries.
  *
  * @param behaviorFactory
  *   creates the entity behavior from its entity id
  * @param getState
  *   constructor of the actor's GetState command from a reply-to ref
  */
abstract class PekkoEntityRepository[Command, Aggregate](
    actorSystem: ActorSystem[?],
    entityKey: EntityTypeKey[Command],
    behaviorFactory: String => Behavior[Command],
    getState: ActorRef[Option[Aggregate]] => Command
)(using Timeout):

  protected given system: ActorSystem[?] = actorSystem
  protected given ec: ExecutionContext = actorSystem.executionContext

  protected val sharding: ClusterSharding = ClusterSharding(system)

  sharding.init(Entity(entityKey)(entityContext => behaviorFactory(entityContext.entityId)))

  protected def entityRef(entityId: String): EntityRef[Command] =
    sharding.entityRefFor(entityKey, entityId)

  protected def findByEntityId(entityId: String): Future[Option[Aggregate]] =
    entityRef(entityId).ask(getState)

  /** Fans out entries to individual entity saves. Not transactional: individual entries may
    * succeed or fail independently.
    */
  protected final def sequenceSaves[SavedAggregate, Event](
      entries: List[(SavedAggregate, Event)]
  )(saveOne: (SavedAggregate, Event) => Future[Unit]): Future[Unit] =
    Future.sequence(entries.map(saveOne.tupled)).map(_ => ())
