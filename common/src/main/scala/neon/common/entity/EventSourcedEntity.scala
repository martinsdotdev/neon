package neon.common.entity

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}

import scala.reflect.ClassTag

/** Assembles the uniform event-sourced entity behavior shared by every aggregate actor: MDC
  * tagging, enforced replies, a persistence id derived from the entity key, snapshot retention
  * every 100 events keeping 2, and the standard received-command debug log.
  *
  * Serialization guardrails: this object only assembles behavior. Commands, events, responses,
  * and state classes stay concrete types nested in each per-module actor object — never move,
  * rename, or genericize them, because Jackson CBOR manifests are fully-qualified class names
  * baked into the journal and snapshots. Always pass the type arguments explicitly at call sites
  * (`behavior[Command, WaveEvent, State](...)`); letting `emptyState` drive inference would
  * narrow `State` to the `EmptyState` singleton type.
  */
object EventSourcedEntity:

  def behavior[Command: ClassTag, Event, State](
      entityKey: EntityTypeKey[Command],
      entityId: String,
      emptyState: State,
      commandHandler: ActorContext[Command] => (State, Command) => ReplyEffect[Event, State],
      eventHandler: (State, Event) => State
  ): Behavior[Command] =
    Behaviors.withMdc[Command](
      Map("entityType" -> entityKey.name, "entityId" -> entityId)
    ):
      Behaviors.setup: context =>
        val handler = commandHandler(context)
        EventSourcedBehavior
          .withEnforcedReplies[Command, Event, State](
            persistenceId = PersistenceId(entityKey.name, entityId),
            emptyState = emptyState,
            commandHandler = { (state, command) =>
              context.log.debug(
                "Received {} in state {}",
                command.getClass.getSimpleName,
                state.getClass.getSimpleName
              )
              handler(state, command)
            },
            eventHandler = eventHandler
          )
          .withRetention(
            RetentionCriteria.snapshotEvery(100, 2)
          )

  /** Standard rejection message used by the per-actor unknown-command fallbacks. */
  def invalidCommandMessage(state: Any, command: Any): String =
    s"Invalid command ${command.getClass.getSimpleName} " +
      s"in state ${state.getClass.getSimpleName}"
