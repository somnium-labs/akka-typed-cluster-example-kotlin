package com.labs.somnium.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.labs.somnium.serializer.EventSerializeMarker
import com.labs.somnium.serializer.MsgSerializeMarker

class ArtifactStateEntityActor private constructor(entityId: String) :
    EventSourcedBehavior<ArtifactStateEntityActor.ArtifactCommand, ArtifactStateEntityActor.ArtifactEvent, ArtifactStateEntityActor.CurrState>(
        PersistenceId.apply(ArtifactStatesShardName, entityId)
    ) {
    companion object {
        const val ArtifactStatesShardName = "ArtifactState"
        fun create(entityId: String): Behavior<ArtifactCommand> = ArtifactStateEntityActor(entityId)
    }

    sealed interface BaseId : MsgSerializeMarker {
        val artifactId: Long
        val userId: String
    }

    sealed interface ArtifactCommand : BaseId
    sealed interface ArtifactQuery : ArtifactCommand
    sealed interface ArtifactResponse : MsgSerializeMarker

    // queries
    data class IsArtifactReadByUser(
        val replyTo: ActorRef<ArtifactReadByUser>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactQuery

    data class IsArtifactInUserFeed(
        val replyTo: ActorRef<ArtifactInUserFeed>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactQuery

    data class GetAllStates(
        val replyTo: ActorRef<AllStates>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactQuery

    // commands
    data class SetArtifactRead(
        val replyTo: ActorRef<Okay>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactCommand

    data class SetArtifactAddedToUserFeed(
        val replyTo: ActorRef<Okay>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactCommand

    data class SetArtifactRemovedFromUserFeed(
        val replyTo: ActorRef<Okay>,
        override val artifactId: Long,
        override val userId: String
    ) : ArtifactCommand

    // responses
    data class Okay(val okay: String = "OK") : ArtifactResponse
    data class ArtifactReadByUser(val artifactRead: Boolean) : ArtifactResponse
    data class ArtifactInUserFeed(val artifactInUserFeed: Boolean) : ArtifactResponse
    data class AllStates(val artifactRead: Boolean, val artifactInUserFeed: Boolean) : ArtifactResponse

    // events
    sealed interface ArtifactEvent : EventSerializeMarker
    data class ArtifactRead(val mark: String) : ArtifactEvent
    object ArtifactAddedToUserFeed : ArtifactEvent
    object ArtifactRemovedFromUserFeed : ArtifactEvent

    // 
    data class CurrState(
        val artifactRead: Boolean = false,
        val artifactInUserFeed: Boolean = false
    ) : MsgSerializeMarker

    override fun emptyState(): CurrState {
        return CurrState()
    }

    override fun commandHandler(): CommandHandler<ArtifactCommand, ArtifactEvent, CurrState> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(SetArtifactRead::class.java, ::onSetArtifactRead)
            .onCommand(SetArtifactAddedToUserFeed::class.java, ::onSetArtifactAddedToUserFeed)
            .onCommand(SetArtifactRemovedFromUserFeed::class.java, ::onSetArtifactRemovedFromUserFeed)
            .onCommand(IsArtifactReadByUser::class.java, ::onIsArtifactReadByUser)
            .onCommand(IsArtifactInUserFeed::class.java, ::onIsArtifactInUserFeed)
            .onCommand(GetAllStates::class.java, ::onGetAllStates)
            .build()
    }

    private fun onSetArtifactRead(setArtifactRead: SetArtifactRead): Effect<ArtifactEvent, CurrState> {
        return Effect().persist(ArtifactRead("Mike was here")).thenRun { setArtifactRead.replyTo.tell(Okay()) }
    }

    private fun onSetArtifactAddedToUserFeed(setArtifactAddedToUserFeed: SetArtifactAddedToUserFeed): Effect<ArtifactEvent, CurrState> {
        return Effect().persist(ArtifactAddedToUserFeed).thenRun { setArtifactAddedToUserFeed.replyTo.tell(Okay()) }
    }

    private fun onSetArtifactRemovedFromUserFeed(setArtifactRemovedFromUserFeed: SetArtifactRemovedFromUserFeed): Effect<ArtifactEvent, CurrState> {
        return Effect().persist(ArtifactAddedToUserFeed).thenRun { setArtifactRemovedFromUserFeed.replyTo.tell(Okay()) }
    }

    private fun onIsArtifactReadByUser(
        state: CurrState,
        isArtifactReadByUser: IsArtifactReadByUser
    ): Effect<ArtifactEvent, CurrState> {
        isArtifactReadByUser.replyTo.tell(ArtifactReadByUser(state.artifactRead))
        return Effect().none()
    }

    private fun onIsArtifactInUserFeed(
        state: CurrState,
        isArtifactInUserFeed: IsArtifactInUserFeed
    ): Effect<ArtifactEvent, CurrState> {
        isArtifactInUserFeed.replyTo.tell(ArtifactInUserFeed(state.artifactInUserFeed))
        return Effect().none()
    }

    private fun onGetAllStates(state: CurrState, getAllStates: GetAllStates): Effect<ArtifactEvent, CurrState> {
        getAllStates.replyTo.tell(AllStates(state.artifactRead, state.artifactInUserFeed))
        return Effect().none()
    }

    override fun eventHandler(): EventHandler<CurrState, ArtifactEvent> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(ArtifactRead::class.java, ::onArtifactRead)
            .onEvent(ArtifactAddedToUserFeed::class.java, ::onArtifactAddedToUserFeed)
            .onEvent(ArtifactRemovedFromUserFeed::class.java, ::onArtifactRemovedFromUserFeed)
            .build()

        // TODO: not matched
        //  throw IllegalStateException("unexpected event [$event] in state [$state]")
    }

    private fun onArtifactRead(state: CurrState, event: ArtifactEvent): CurrState {
        return CurrState(artifactRead = true, artifactInUserFeed = state.artifactInUserFeed)
    }

    private fun onArtifactAddedToUserFeed(state: CurrState, event: ArtifactAddedToUserFeed): CurrState {
        return CurrState(state.artifactRead, artifactInUserFeed = true)
    }

    private fun onArtifactRemovedFromUserFeed(state: CurrState, event: ArtifactRemovedFromUserFeed): CurrState {
        return CurrState(state.artifactRead)
    }
}
