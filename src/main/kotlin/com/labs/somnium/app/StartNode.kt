package com.labs.somnium.app

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.cluster.typed.Cluster
import akka.http.javadsl.Http
import akka.http.javadsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import com.labs.somnium.actors.ArtifactStateEntityActor
import com.labs.somnium.actors.ArtifactStateEntityActor.Companion.ArtifactStatesShardName
import com.labs.somnium.actors.ClusterListenerActor
import com.labs.somnium.endpoint.ArtifactStateRoutes
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContextExecutor
import java.util.concurrent.CompletionStage

fun main(args: Array<String>) {
    val appConfig = ConfigFactory.load()
    val clusterName = appConfig.getString("clustering.cluster.name")
    val clusterPort = appConfig.getInt("clustering.port")
    val seedPort = appConfig.getInt("clustering.seed-port")
    if (appConfig.hasPath("clustering.ports")) {
        val clusterPorts = appConfig.getIntList("clustering.ports")
        clusterPorts.forEach { port ->
            startNode(port, seedPort, clusterName, appConfig)
        }
    } else {
        startNode(clusterPort, seedPort, clusterName, appConfig)
    }
}

fun startNode(port: Int, seedPort: Int, clusterName: String, appConfig: Config): CompletionStage<Done> {
    val rootBehavior = Behaviors.setup { context: ActorContext<Any> ->
        val classicSystem: akka.actor.ActorSystem = context.system.classicSystem()
        val typeKey = EntityTypeKey.create(
            ArtifactStateEntityActor.ArtifactCommand::class.java,
            ArtifactStatesShardName
        )

        val cluster = try {
            Cluster.get(context.system)
        } catch (e: Exception) {
            println(e.message)
            throw e
        }

        context.log.info("starting node with roles: ${cluster.selfMember().roles}")

        if (cluster.selfMember().hasRole("k8s")) {
            AkkaManagement.get(classicSystem).start()
            ClusterBootstrap.get(classicSystem).start()
        }

        if (cluster.selfMember().hasRole("sharded")) {
            val entity = Entity.of(typeKey) { ArtifactStateEntityActor.create(it.entityId) }
                .withSettings(ClusterShardingSettings.apply(context.system).withRole("sharded"))
            ClusterSharding.get(context.system).init(entity)
        } else {
            if (cluster.selfMember().hasRole("endpoint")) {
                val ec: ExecutionContextExecutor = context.system.executionContext()
                val entity = Entity.of(typeKey) { ArtifactStateEntityActor.create(it.entityId) }
                val psEntities = ClusterSharding.get(context.system).init(entity)
                val psCommandActor: ActorRef<ShardingEnvelope<ArtifactStateEntityActor.ArtifactCommand>> = psEntities
                val routes: Route = ArtifactStateRoutes(context.system, psCommandActor).psRoutes
                val httpPort = context.system.settings().config().getInt("akka.http.server.default-http-port")
                val `interface` =
                    if (!cluster.selfMember().hasRole("docker") && !cluster.selfMember().hasRole("k8s")) {
                        "localhost"
                    } else {
                        "0.0.0.0"
                    }

                val binding = Http.get(classicSystem).newServerAt(`interface`, httpPort).bind(routes)

                // report successful binding
                binding.thenAcceptAsync({
                    println("Server online inside container on ip ${it.localAddress()} port $httpPort")
                }, ec)
            }
        }

        if (port == seedPort) {
            context.spawn(ClusterListenerActor.create(), "clusterListenerActor")
            context.log.info("started clusterListenerActor")
        }

        Behaviors.empty()
    }

    val system = ActorSystem.create(rootBehavior, clusterName, appConfig)
    return system.whenTerminated
}
