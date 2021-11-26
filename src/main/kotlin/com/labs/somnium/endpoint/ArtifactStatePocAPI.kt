package com.labs.somnium.endpoint

class ArtifactStatePocAPI {
    data class ArtifactAndUser(val artifactId: Long = 1, val userId: String = "test")

    sealed interface ExtResponses

    data class ExtResponse(
        val artifactId: Long,
        val userId: String,
        val answer: Boolean?,
        val failureMsg: String? = null
    ) : ExtResponses

    data class AllStatesResponse(
        val artifactId: Long,
        val userId: String,
        val artifactRead: Boolean?,
        val artifactInUserFeed: Boolean?,
        val failureMsg: String? = null,
    ) : ExtResponses

    data class CommandResponse(val success: Boolean) : ExtResponses
}
