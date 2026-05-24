package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest
import software.amazon.awssdk.services.secretsmanager.model.Tag
import software.amazon.awssdk.services.secretsmanager.model.TagResourceRequest
import software.amazon.awssdk.services.secretsmanager.model.UntagResourceRequest
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretVersionStageRequest
import java.time.Instant
import java.util.UUID

data class SecretVersionInfo(
    val versionId: String,
    val stages: Set<String>,
)

data class SecretRotationResult(
    val secretId: String,
    val previousVersionId: String?,
    val activeVersionId: String,
    val overlapUntilEpochMillis: Long? = null,
)

data class SecretLifecycleStatus(
    val secretId: String,
    val disabled: Boolean,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val rotationPhase: String? = null,
)

@ApplicationScoped
class AwsSecretsManagerService
{
    companion object
    {
        private const val TAG_STATUS = "app-status"
        private const val TAG_OVERLAP_UNTIL = "app-rotation-overlap-until"
        private const val TAG_ROTATION_PHASE = "app-rotation-phase"

        private const val PHASE_PREPARE = "PREPARE"
        private const val PHASE_VALIDATE = "VALIDATE"
        private const val PHASE_ACTIVATE = "ACTIVATE"
        private const val PHASE_MONITOR = "MONITOR"
        private const val PHASE_ROLLBACK = "ROLLBACK"
        private const val PHASE_RETIRE = "RETIRE"

        private val allowedTransitions: Map<String, Set<String>> = mapOf(
            PHASE_PREPARE to setOf(PHASE_PREPARE, PHASE_VALIDATE, PHASE_ACTIVATE, PHASE_MONITOR, PHASE_ROLLBACK, PHASE_RETIRE),
            PHASE_VALIDATE to setOf(PHASE_VALIDATE, PHASE_ACTIVATE, PHASE_ROLLBACK, PHASE_RETIRE),
            PHASE_ACTIVATE to setOf(PHASE_ACTIVATE, PHASE_MONITOR, PHASE_ROLLBACK, PHASE_RETIRE),
            PHASE_MONITOR to setOf(PHASE_MONITOR, PHASE_PREPARE, PHASE_ROLLBACK, PHASE_RETIRE),
            PHASE_ROLLBACK to setOf(PHASE_ROLLBACK, PHASE_MONITOR, PHASE_PREPARE, PHASE_RETIRE),
            PHASE_RETIRE to setOf(PHASE_RETIRE),
        )
    }

    fun getSecretString(secretId: String, region: String, versionStage: String? = null): String
    {
        return withClient(region) { client ->
            val requestBuilder = GetSecretValueRequest.builder().secretId(secretId)
            if (!versionStage.isNullOrBlank())
            {
                requestBuilder.versionStage(versionStage)
            }
            val response = client.getSecretValue(requestBuilder.build())
            response.secretString() ?: ""
        }
    }

    fun createSecret(secretId: String, secretString: String, region: String): String
    {
        return withClient(region) { client ->
            val request = CreateSecretRequest.builder()
                .name(secretId)
                .secretString(secretString)
                .clientRequestToken(UUID.randomUUID().toString())
                .build()
            val response = client.createSecret(request)
            response.arn() ?: secretId
        }
    }

    fun putSecretVersion(secretId: String, secretString: String, region: String, stages: List<String> = emptyList()): String
    {
        return withClient(region) { client ->
            val requestBuilder = software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest.builder()
                .secretId(secretId)
                .secretString(secretString)
                .clientRequestToken(UUID.randomUUID().toString())
            if (stages.isNotEmpty())
            {
                requestBuilder.versionStages(stages)
            }
            val response = client.putSecretValue(requestBuilder.build())
            response.versionId() ?: throw IllegalStateException("Secrets Manager did not return a version ID")
        }
    }

    fun listSecretVersions(secretId: String, region: String): List<SecretVersionInfo>
    {
        return withClient(region) { client ->
            val response = client.listSecretVersionIds(
                ListSecretVersionIdsRequest.builder().secretId(secretId).build()
            )
            response.versions().map { version ->
                SecretVersionInfo(
                    versionId = version.versionId(),
                    stages = version.versionStages().toSet(),
                )
            }
        }
    }

    fun rotateSecret(secretId: String, newSecret: String, region: String): SecretRotationResult
    {
        return rotateSecret(secretId, newSecret, region, overlapHours = 0)
    }

    fun rotateSecret(secretId: String, newSecret: String, region: String, overlapHours: Long): SecretRotationResult
    {
        setRotationPhase(secretId, region, "PREPARE")
        val versions = listSecretVersions(secretId, region)
        val previousCurrent = versions.firstOrNull { it.stages.contains("AWSCURRENT") }?.versionId
        val pendingVersion = putSecretVersion(secretId, newSecret, region, stages = listOf("AWSPENDING"))

        setRotationPhase(secretId, region, "VALIDATE")
        activateSecretVersion(secretId, pendingVersion, region)
        setRotationPhase(secretId, region, "ACTIVATE")

        val overlapUntil = if (overlapHours > 0) Instant.now().plusSeconds(overlapHours * 3600).toEpochMilli() else null
        tagOverlapUntil(secretId, region, overlapUntil)
        setRotationPhase(secretId, region, "MONITOR")

        if (!previousCurrent.isNullOrBlank())
        {
            withClient(region) { client ->
                client.updateSecretVersionStage(
                    UpdateSecretVersionStageRequest.builder()
                        .secretId(secretId)
                        .versionStage("AWSPREVIOUS")
                        .moveToVersionId(previousCurrent)
                        .build()
                )
            }
        }

        return SecretRotationResult(
            secretId = secretId,
            previousVersionId = previousCurrent,
            activeVersionId = pendingVersion,
            overlapUntilEpochMillis = overlapUntil,
        )
    }

    fun activateSecretVersion(secretId: String, versionId: String, region: String)
    {
        val currentVersion = listSecretVersions(secretId, region)
            .firstOrNull { it.stages.contains("AWSCURRENT") }
            ?.versionId

        withClient(region) { client ->
            val requestBuilder = UpdateSecretVersionStageRequest.builder()
                .secretId(secretId)
                .versionStage("AWSCURRENT")
                .moveToVersionId(versionId)
            if (!currentVersion.isNullOrBlank() && currentVersion != versionId)
            {
                requestBuilder.removeFromVersionId(currentVersion)
            }
            client.updateSecretVersionStage(requestBuilder.build())
        }
    }

    fun disableSecret(secretId: String, region: String)
    {
        withClient(region) { client ->
            client.tagResource(
                TagResourceRequest.builder()
                    .secretId(secretId)
                    .tags(Tag.builder().key("app-status").value("DISABLED").build())
                    .build()
            )
        }
    }

    fun enableSecret(secretId: String, region: String)
    {
        withClient(region) { client ->
            client.untagResource(
                UntagResourceRequest.builder()
                    .secretId(secretId)
                    .tagKeys("app-status")
                    .build()
            )
            client.tagResource(
                TagResourceRequest.builder()
                    .secretId(secretId)
                    .tags(Tag.builder().key("app-status").value("ACTIVE").build())
                    .build()
            )
        }
    }

    fun isSecretDisabled(secretId: String, region: String): Boolean
    {
        val status = getSecretLifecycleStatus(secretId, region)
        return status.disabled
    }

    fun getSecretLifecycleStatus(secretId: String, region: String): SecretLifecycleStatus
    {
        return withClient(region) { client ->
            val response = client.describeSecret(
                software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest.builder()
                    .secretId(secretId)
                    .build()
            )
            val versions = listSecretVersions(secretId, region)
            val tags = response.tags().associate { it.key() to (it.value() ?: "") }

            SecretLifecycleStatus(
                secretId = secretId,
                disabled = tags[TAG_STATUS].equals("DISABLED", ignoreCase = true),
                currentVersionId = versions.firstOrNull { it.stages.contains("AWSCURRENT") }?.versionId,
                previousVersionId = versions.firstOrNull { it.stages.contains("AWSPREVIOUS") }?.versionId,
                pendingVersionId = versions.firstOrNull { it.stages.contains("AWSPENDING") }?.versionId,
                overlapUntilEpochMillis = tags[TAG_OVERLAP_UNTIL]?.toLongOrNull(),
                rotationPhase = tags[TAG_ROTATION_PHASE]?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun setRotationPhase(secretId: String, region: String, phase: String)
    {
        val normalizedPhase = phase.trim().uppercase()
        if (normalizedPhase.isBlank())
        {
            return
        }

        if (!allowedTransitions.containsKey(normalizedPhase))
        {
            throw IllegalArgumentException("Unsupported rotation phase: $normalizedPhase")
        }

        val currentPhase = getCurrentRotationPhase(secretId, region)
        if (!isTransitionAllowed(currentPhase, normalizedPhase))
        {
            throw IllegalArgumentException("Invalid rotation phase transition from ${currentPhase ?: "NONE"} to $normalizedPhase")
        }

        withClient(region) { client ->
            client.tagResource(
                TagResourceRequest.builder()
                    .secretId(secretId)
                    .tags(Tag.builder().key(TAG_ROTATION_PHASE).value(normalizedPhase).build())
                    .build()
            )
        }
    }

    private fun getCurrentRotationPhase(secretId: String, region: String): String?
    {
        return withClient(region) { client ->
            val response = client.describeSecret(
                software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest.builder()
                    .secretId(secretId)
                    .build()
            )
            response.tags()
                .firstOrNull { it.key() == TAG_ROTATION_PHASE }
                ?.value()
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun isTransitionAllowed(currentPhase: String?, targetPhase: String): Boolean
    {
        if (currentPhase.isNullOrBlank())
        {
            return true
        }

        return allowedTransitions[currentPhase]?.contains(targetPhase) == true
    }

    fun retireSecret(secretId: String, region: String, recoveryWindowInDays: Int = 7)
    {
        withClient(region) { client ->
            client.deleteSecret(
                DeleteSecretRequest.builder()
                    .secretId(secretId)
                    .recoveryWindowInDays(recoveryWindowInDays.toLong())
                    .build()
            )
        }
    }

    private fun tagOverlapUntil(secretId: String, region: String, overlapUntilEpochMillis: Long?)
    {
        withClient(region) { client ->
            client.untagResource(
                UntagResourceRequest.builder()
                    .secretId(secretId)
                    .tagKeys(TAG_OVERLAP_UNTIL)
                    .build()
            )

            if (overlapUntilEpochMillis != null)
            {
                client.tagResource(
                    TagResourceRequest.builder()
                        .secretId(secretId)
                        .tags(Tag.builder().key(TAG_OVERLAP_UNTIL).value(overlapUntilEpochMillis.toString()).build())
                        .build()
                )
            }
        }
    }

    private fun <T> withClient(region: String, block: (SecretsManagerClient) -> T): T
    {
        return SecretsManagerClient.builder()
            .region(Region.of(region))
            .build()
            .use(block)
    }
}

