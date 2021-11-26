@file:Suppress("UNCHECKED_CAST")

package com.labs.somnium.endpoint

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Scheduler
import akka.actor.typed.javadsl.AskPattern
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.javadsl.marshallers.jackson.Jackson.marshaller
import akka.http.javadsl.marshallers.jackson.Jackson.unmarshaller
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.labs.somnium.actors.ArtifactStateEntityActor
import java.time.Duration
import java.util.concurrent.CompletionStage

class ArtifactStateRoutes(
    private val system: ActorSystem<*>,
    private val psCommandActor: ActorRef<ShardingEnvelope<ArtifactStateEntityActor.ArtifactCommand>>
) : AllDirectives() {

    private val ec = system.executionContext()

    init {
        timeout = system.settings().config().getDuration("app.routes.ask-timeout")
        scheduler = system.scheduler()
        objectMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build()
    }

    companion object {
        lateinit var timeout: Duration
        lateinit var scheduler: Scheduler
        lateinit var objectMapper: JsonMapper
    }

    private fun handleResponse(
        req: ArtifactStatePocAPI.ArtifactAndUser,
        result: CompletionStage<ArtifactStateEntityActor.ArtifactResponse>
    ): CompletionStage<ArtifactStatePocAPI.ExtResponse> {
        return result.whenCompleteAsync({ response, _ ->
            when (response) {
                is ArtifactStateEntityActor.ArtifactReadByUser -> ArtifactStatePocAPI.ExtResponse(
                    req.artifactId,
                    req.userId,
                    response.artifactRead,
                )
                is ArtifactStateEntityActor.ArtifactInUserFeed -> ArtifactStatePocAPI.ExtResponse(
                    req.artifactId,
                    req.userId,
                    response.artifactInUserFeed,
                )
                else -> ArtifactStatePocAPI.ExtResponse(
                    req.artifactId,
                    req.userId,
                    answer = null,
                    failureMsg = "Internal Query Error: this shouldn't happen."
                )
            }
        }, ec) as CompletionStage<ArtifactStatePocAPI.ExtResponse>
    }

    private fun queryArtifactRead(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.ExtResponse> {
        val response = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.ArtifactReadByUser> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.IsArtifactReadByUser(ref, req.artifactId, req.userId)
            )
        }
        return handleResponse(req, response as CompletionStage<ArtifactStateEntityActor.ArtifactResponse>)
    }

    private fun queryArtifactInUserFeed(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.ExtResponse> {
        val response = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.ArtifactInUserFeed> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.IsArtifactInUserFeed(ref, req.artifactId, req.userId)
            )
        }
        return handleResponse(req, response as CompletionStage<ArtifactStateEntityActor.ArtifactResponse>)
    }

    private fun queryAllStates(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.AllStatesResponse> {
        val result = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.AllStates> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.GetAllStates(ref, req.artifactId, req.userId)
            )
        }

        return result.whenCompleteAsync({ response, ex ->
            if (ex == null) {
                ArtifactStatePocAPI.AllStatesResponse(
                    req.artifactId,
                    req.userId,
                    response.artifactRead,
                    response.artifactInUserFeed,
                )
            } else {
                system.log().error(ex.message, ex)
                ArtifactStatePocAPI.AllStatesResponse(req.artifactId, req.userId, null, null, ex.message)
            }
        }, ec) as CompletionStage<ArtifactStatePocAPI.AllStatesResponse>
    }

    private fun handleCmdResponse(
        req: ArtifactStatePocAPI.ArtifactAndUser,
        result: CompletionStage<ArtifactStateEntityActor.ArtifactResponse>
    ): CompletionStage<ArtifactStatePocAPI.CommandResponse> {
        return result.whenCompleteAsync({ response, ex ->
            if (ex == null) {
                when (response) {
                    is ArtifactStateEntityActor.Okay -> ArtifactStatePocAPI.CommandResponse(true)
                    else -> {
                        system.log().error("Internal Command Error: this shouldn't happen.")
                        ArtifactStatePocAPI.CommandResponse(false)
                    }
                }
            } else {
                system.log()
                    .error("failure on request user: ${req.userId} artifact id: ${req.artifactId} ${ex.message}", ex)
                ArtifactStatePocAPI.CommandResponse(false)
            }
        }, ec) as CompletionStage<ArtifactStatePocAPI.CommandResponse>
    }

    private fun cmdArtifactRead(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.CommandResponse> {
        val result = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.Okay> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.SetArtifactRead(ref, req.artifactId, req.userId)
            )
        }
        return handleCmdResponse(req, result as CompletionStage<ArtifactStateEntityActor.ArtifactResponse>)
    }

    private fun cmdArtifactAddedToUserFeed(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.CommandResponse> {
        val result = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.Okay> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.SetArtifactAddedToUserFeed(ref, req.artifactId, req.userId)
            )
        }
        return handleCmdResponse(req, result as CompletionStage<ArtifactStateEntityActor.ArtifactResponse>)
    }

    private fun cmdArtifactRemovedFromUserFeed(req: ArtifactStatePocAPI.ArtifactAndUser): CompletionStage<ArtifactStatePocAPI.CommandResponse> {
        val result = psCommandActor.ask { ref: ActorRef<ArtifactStateEntityActor.Okay> ->
            ShardingEnvelope(
                "${req.artifactId}${req.userId}",
                ArtifactStateEntityActor.SetArtifactRemovedFromUserFeed(ref, req.artifactId, req.userId)
            )
        }
        return handleCmdResponse(req, result as CompletionStage<ArtifactStateEntityActor.ArtifactResponse>)
    }

    val psRoutes: Route = pathPrefix("artifactState") {
        concat(
            pathPrefix("isArtifactReadByUser") {
                concat(
                    get {
                        parameter("artifactId") { artifactId ->
                            parameter("userId") { userId ->
                                completeOKWithFuture(
                                    queryArtifactRead(ArtifactStatePocAPI.ArtifactAndUser(artifactId.toLong(), userId)),
                                    marshaller()
                                )
                            }
                        }
                    },
                    post {
                        entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                            completeOKWithFuture(queryArtifactRead(it), marshaller())
                        }
                    }
                )
            },
            pathPrefix("isArtifactInUserFeed") {
                concat(
                    get {
                        parameter("artifactId") { artifactId ->
                            parameter("userId") { userId ->
                                val req = ArtifactStatePocAPI.ArtifactAndUser(artifactId.toLong(), userId)
                                completeOKWithFuture(queryArtifactInUserFeed(req), marshaller())
                            }
                        }
                    },
                    post {
                        entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                            completeOKWithFuture(queryArtifactInUserFeed(it), marshaller())
                        }
                    }
                )
            },
            pathPrefix("getAllStates") {
                concat(
                    get {
                        parameter("artifactId") { artifactId ->
                            parameter("userId") { userId ->
                                val req = ArtifactStatePocAPI.ArtifactAndUser(artifactId.toLong(), userId)
                                completeOKWithFuture(queryAllStates(req), marshaller())
                            }
                        }
                    },
                    post {
                        entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                            completeOKWithFuture(queryAllStates(it), marshaller())
                        }
                    }
                )
            },
            // COMMANDS:
            pathPrefix("setArtifactReadByUser") {
                post {
                    entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                        completeOKWithFuture(cmdArtifactRead(it), marshaller())
                    }
                }
            },
            pathPrefix("setArtifactAddedToUserFeed") {
                post {
                    entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                        completeOKWithFuture(cmdArtifactAddedToUserFeed(it), marshaller())
                    }
                }
            },
            pathPrefix("setArtifactRemovedFromUserFeed") {
                post {
                    entity(unmarshaller(ArtifactStatePocAPI.ArtifactAndUser::class.java)) {
                        completeOKWithFuture(cmdArtifactRemovedFromUserFeed(it), marshaller())
                    }
                }
            },
        )
    }

//    private fun <T> unmarshaller(expectedType: Class<T>): Unmarshaller<HttpEntity, T> {
//        return Jackson.unmarshaller(objectMapper, expectedType)
//    }
//
//    private fun <T> marshaller(): Marshaller<T, RequestEntity> {
//        return Jackson.marshaller(objectMapper)
//    }

    private fun <Request, Response> ActorRef<Request>.ask(
        timeout: Duration = ArtifactStateRoutes.timeout,
        scheduler: Scheduler = ArtifactStateRoutes.scheduler,
        f: (ActorRef<Response>) -> Request
    ): CompletionStage<Response> {
        return AskPattern.ask(this, f, timeout, scheduler)
    }
}
