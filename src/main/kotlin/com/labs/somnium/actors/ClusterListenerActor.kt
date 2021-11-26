package com.labs.somnium.actors

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.ClusterEvent
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe

class ClusterListenerActor(context: ActorContext<ClusterEvent.ClusterDomainEvent>) :
    AbstractBehavior<ClusterEvent.ClusterDomainEvent>(context) {

    private val cluster = Cluster.get(context.system)

    init {
        cluster.subscriptions().tell(Subscribe.create(context.self, ClusterEvent.ClusterDomainEvent::class.java))
        context.log.info("started actor ${context.self.path()} - (${context.self.javaClass})")
    }

    override fun createReceive(): Receive<ClusterEvent.ClusterDomainEvent> {
        return newReceiveBuilder()
            .onMessage(ClusterEvent.MemberUp::class.java) {
                context.log.info("Member is Up: {}", it.member().address())
                Behaviors.same()
            }
            .onMessage(ClusterEvent.UnreachableMember::class.java) {
                context.log.info("Member detected as unreachable: {}", it.member())
                Behaviors.same()
            }
            .onMessage(ClusterEvent.MemberRemoved::class.java) {
                context.log.info(
                    "Member is Removed: {} after {}",
                    it.member().address(), it.previousStatus()
                )
                Behaviors.same()
            }.onAnyMessage {
                Behaviors.same()
            }
            .build()
    }

    companion object {
        fun create(): Behavior<ClusterEvent.ClusterDomainEvent> {
            return Behaviors.setup(::ClusterListenerActor)
        }
    }
}
