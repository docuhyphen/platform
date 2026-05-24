package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.UUID

data class OrganizationIdpRotationPreviewCandidate(
    val configId: UUID,
    val provider: String,
    val secretRef: String,
    val updatedDate: Instant,
    val due: Boolean,
    val overdueByDays: Long,
)

data class OrganizationIdpRotationPreviewResult(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val candidates: List<OrganizationIdpRotationPreviewCandidate>,
)

data class OrganizationIdpRotationRunResult(
    val evaluated: Int,
    val rotated: Int,
    val failed: Int,
    val skipped: Int,
)

data class OrganizationIdpRotationStatusItem(
    val configId: UUID,
    val provider: String,
    val secretRef: String,
    val disabled: Boolean,
    val rotationPhase: String? = null,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val overlapActive: Boolean,
    val due: Boolean,
    val overdueByDays: Long,
)

data class OrganizationIdpRotationStatusResult(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val overlapActiveCount: Int,
    val disabledCount: Int,
    val retirePhaseCount: Int,
    val items: List<OrganizationIdpRotationStatusItem>,
)

@ApplicationScoped
class OrganizationIdpSecretRotationSchedulerService @Inject constructor(
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val awsSecretsManagerService: AwsSecretsManagerService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val securityIncidentService: SecurityIncidentService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdpSecretRotationSchedulerService::class.java)
        private val random = SecureRandom()
    }

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    fun onStart(@Observes event: StartupEvent)
    {
        scheduler.scheduleAtFixedRate(
            { runRotationCycle() },
            60,
            TimeUnit.DAYS.toSeconds(1),
            TimeUnit.SECONDS,
        )
    }

    @PreDestroy
    fun onStop()
    {
        scheduler.shutdownNow()
    }

    fun runRotationCycle()
    {
        if (!configurationService.isSecretsRotationEnabled())
        {
            return
        }

        val intervalDays = configurationService.getSecretsRotationIntervalDays()
        if (intervalDays <= 0)
        {
            logger.warn("Skipping scheduled org IdP secret rotation due to invalid intervalDays={}", intervalDays)
            return
        }

        val dueThreshold = Instant.now().minus(intervalDays, ChronoUnit.DAYS)
        val configs = organizationIdentityProviderConfigRepository.findAll()
            .filter { it.isActive }
            .filter { !it.clientSecretRef.isBlank() }
            .filter { it.updatedDate.toInstant().isBefore(dueThreshold) }

        rotateConfigs(
            configs = configs,
            action = "ORG_IDP_SECRET_ROTATION_JOB",
            reason = "Scheduled organization IdP secret rotation",
            actorId = null,
            requestId = null,
        )
    }

    fun runManualRotationForOrganization(organizationId: UUID, actorId: UUID?, requestId: String?): OrganizationIdpRotationRunResult
    {
        val configs = organizationIdentityProviderConfigRepository.findByOrganizationId(organizationId)
            .filter { it.isActive }
            .filter { !it.clientSecretRef.isBlank() }

        return rotateConfigs(
            configs = configs,
            action = "ORG_IDP_SECRET_ROTATION_RUNBOOK",
            reason = "Manual emergency organization IdP secret rotation runbook",
            actorId = actorId,
            requestId = requestId,
        )
    }

    fun previewRotationForOrganization(organizationId: UUID): OrganizationIdpRotationPreviewResult
    {
        val intervalDays = configurationService.getSecretsRotationIntervalDays().coerceAtLeast(1)
        val now = Instant.now()
        val dueThreshold = now.minus(intervalDays, ChronoUnit.DAYS)

        val candidates = organizationIdentityProviderConfigRepository.findByOrganizationId(organizationId)
            .filter { it.isActive }
            .filter { !it.clientSecretRef.isBlank() }
            .map { config ->
                val updated = config.updatedDate.toInstant()
                val due = updated.isBefore(dueThreshold)
                val ageDays = ChronoUnit.DAYS.between(updated, now).coerceAtLeast(0)
                val overdueByDays = (ageDays - intervalDays).coerceAtLeast(0)

                OrganizationIdpRotationPreviewCandidate(
                    configId = config.id,
                    provider = config.provider,
                    secretRef = config.clientSecretRef,
                    updatedDate = updated,
                    due = due,
                    overdueByDays = overdueByDays,
                )
            }

        return OrganizationIdpRotationPreviewResult(
            intervalDays = intervalDays,
            evaluated = candidates.size,
            dueCount = candidates.count { it.due },
            candidates = candidates,
        )
    }

    fun statusForOrganization(organizationId: UUID): OrganizationIdpRotationStatusResult
    {
        val intervalDays = configurationService.getSecretsRotationIntervalDays().coerceAtLeast(1)
        val now = Instant.now()
        val dueThreshold = now.minus(intervalDays, ChronoUnit.DAYS)

        val items = organizationIdentityProviderConfigRepository.findByOrganizationId(organizationId)
            .filter { it.isActive }
            .filter { !it.clientSecretRef.isBlank() }
            .map { config ->
                val updated = config.updatedDate.toInstant()
                val due = updated.isBefore(dueThreshold)
                val ageDays = ChronoUnit.DAYS.between(updated, now).coerceAtLeast(0)
                val overdueByDays = (ageDays - intervalDays).coerceAtLeast(0)

                val lifecycle = awsSecretsManagerService.getSecretLifecycleStatus(
                    secretId = config.clientSecretRef,
                    region = configurationService.getOrgIdpSecretsRegion(),
                )
                val overlapActive = lifecycle.overlapUntilEpochMillis?.let { it > now.toEpochMilli() } ?: false

                OrganizationIdpRotationStatusItem(
                    configId = config.id,
                    provider = config.provider,
                    secretRef = config.clientSecretRef,
                    disabled = lifecycle.disabled,
                    rotationPhase = lifecycle.rotationPhase,
                    currentVersionId = lifecycle.currentVersionId,
                    previousVersionId = lifecycle.previousVersionId,
                    pendingVersionId = lifecycle.pendingVersionId,
                    overlapUntilEpochMillis = lifecycle.overlapUntilEpochMillis,
                    overlapActive = overlapActive,
                    due = due,
                    overdueByDays = overdueByDays,
                )
            }

        return OrganizationIdpRotationStatusResult(
            intervalDays = intervalDays,
            evaluated = items.size,
            dueCount = items.count { it.due },
            overlapActiveCount = items.count { it.overlapActive },
            disabledCount = items.count { it.disabled },
            retirePhaseCount = items.count { it.rotationPhase.equals("RETIRE", ignoreCase = true) },
            items = items,
        )
    }

    private fun rotateConfigs(
        configs: List<OrganizationIdentityProviderConfig>,
        action: String,
        reason: String,
        actorId: UUID?,
        requestId: String?,
    ): OrganizationIdpRotationRunResult
    {
        var rotated = 0
        var failed = 0
        var skipped = 0

        configs.forEach { config ->
            val lifecycle = runCatching {
                awsSecretsManagerService.getSecretLifecycleStatus(
                    secretId = config.clientSecretRef,
                    region = configurationService.getOrgIdpSecretsRegion(),
                )
            }.getOrNull()

            if (lifecycle?.disabled == true || lifecycle?.rotationPhase.equals("RETIRE", ignoreCase = true))
            {
                val skipReason = if (lifecycle?.disabled == true) "DISABLED_SECRET" else "RETIRE_PHASE"
                val skipReasonCode = if (lifecycle?.disabled == true)
                {
                    RevocationReasonCode.ORG_IDP_SECRET_DISABLED
                }
                else
                {
                    RevocationReasonCode.ORG_IDP_SECRET_RETIRE_PENDING
                }
                logger.info("Skipping org IdP secret rotation for configId={} reason={}", config.id, skipReason)
                authAuditService.emit(
                    action = action,
                    outcome = "DENY",
                    actorId = actorId,
                    organizationId = config.organization?.id,
                    requestId = requestId,
                    reasonCode = skipReasonCode,
                    reason = "$reason skipped",
                    beforeSnapshot = "configId=${config.id};secretRef=${config.clientSecretRef};rotationPhase=${lifecycle?.rotationPhase};disabled=${lifecycle?.disabled}",
                    afterSnapshot = "skipReason=$skipReason",
                )
                skipped += 1
                return@forEach
            }

            runCatching {
                val generatedSecret = generateSecretMaterial()
                val rotation = awsSecretsManagerService.rotateSecret(
                    secretId = config.clientSecretRef,
                    newSecret = generatedSecret,
                    region = configurationService.getOrgIdpSecretsRegion(),
                    overlapHours = configurationService.getSecretsRotationOverlapHours().coerceAtLeast(0),
                )

                config.updatedDate = Timestamp.from(Instant.now())
                organizationIdentityProviderConfigRepository.update(config)

                authAuditService.emit(
                    action = action,
                    outcome = "SUCCESS",
                    actorId = actorId,
                    organizationId = config.organization?.id,
                    requestId = requestId,
                    reasonCode = RevocationReasonCode.SYSTEM_MAINTENANCE,
                    reason = reason,
                    beforeSnapshot = "configId=${config.id};secretRef=${config.clientSecretRef};previousVersion=${rotation.previousVersionId}",
                    afterSnapshot = "configId=${config.id};secretRef=${config.clientSecretRef};activeVersion=${rotation.activeVersionId};overlapUntilEpochMillis=${rotation.overlapUntilEpochMillis}",
                )
                rotated += 1
            }.onFailure { error ->
                logger.error("Scheduled org IdP secret rotation failed for configId={}", config.id, error)
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.ORG_IDP_SECRET_ROTATION_FAILURE,
                    severity = SecurityIncidentSeverity.HIGH,
                    actorId = actorId,
                    requestId = requestId,
                    details = "configId=${config.id};organizationId=${config.organization?.id};provider=${config.provider};reason=${error.message}",
                )

                authAuditService.emit(
                    action = action,
                    outcome = "DENY",
                    actorId = actorId,
                    organizationId = config.organization?.id,
                    requestId = requestId,
                    reasonCode = RevocationReasonCode.SYSTEM_MAINTENANCE,
                    reason = "$reason failed",
                    beforeSnapshot = "configId=${config.id};secretRef=${config.clientSecretRef}",
                    afterSnapshot = "error=${error.message}",
                )
                failed += 1
            }
        }

        return OrganizationIdpRotationRunResult(
            evaluated = configs.size,
            rotated = rotated,
            failed = failed,
            skipped = skipped,
        )
    }

    private fun generateSecretMaterial(length: Int = 64): String
    {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_~!@#$%^&*()"
        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }
}











