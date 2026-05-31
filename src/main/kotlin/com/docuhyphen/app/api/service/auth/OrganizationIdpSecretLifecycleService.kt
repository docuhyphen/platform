package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import com.docuhyphen.app.api.service.config.SecretLifecycleStatus
import com.docuhyphen.app.api.service.config.SecretRotationResult
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class OrganizationIdpSecretRotationOutcome(
    val secretRef: String,
    val previousVersionId: String?,
    val activeVersionId: String,
    val overlapUntilEpochMillis: Long? = null,
)

data class OrganizationIdpSecretStatusOutcome(
    val secretRef: String,
    val disabled: Boolean,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val rotationPhase: String? = null,
    val overlapActive: Boolean,
)

data class OrganizationIdpSecretRollbackOutcome(
    val secretRef: String,
    val rolledBackToVersionId: String,
    val previousCurrentVersionId: String? = null,
)

@RequestScoped
class OrganizationIdpSecretLifecycleService @Inject constructor(
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
    private val awsSecretsManagerService: AwsSecretsManagerService,
    private val configurationService: ConfigurationService,
)
{
    @Transactional
    fun rotateClientSecret(
        organizationId: String,
        configId: String,
        newSecret: String,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdpSecretRotationOutcome
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)

        if (newSecret.isBlank())
        {
            throw IllegalArgumentException("New client secret is required")
        }

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_ROTATE",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        val secretRef = requireClientSecretRef(config)
        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "PREPARE")
        val overlapHours = configurationService.getSecretsRotationOverlapHours().coerceAtLeast(0)
        val rotation: SecretRotationResult = awsSecretsManagerService.rotateSecret(
            secretId = secretRef,
            newSecret = newSecret,
            region = configurationService.getOrgIdpSecretsRegion(),
            overlapHours = overlapHours,
        )
        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "MONITOR")

        config.updatedDate = Timestamp.from(Instant.now())
        config.updatedBy = actor.id
        organizationIdentityProviderConfigRepository.update(config)

        authAuditService.emit(
            action = "ORG_IDP_SECRET_ROTATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin rotated IdP client secret",
            beforeSnapshot = "configId=${config.id};secretRef=${secretRef};activeVersion=${rotation.previousVersionId}",
            afterSnapshot = "configId=${config.id};secretRef=${secretRef};activeVersion=${rotation.activeVersionId};overlapUntilEpochMillis=${rotation.overlapUntilEpochMillis};rotationPhase=MONITOR",
        )

        return OrganizationIdpSecretRotationOutcome(
            secretRef = secretRef,
            previousVersionId = rotation.previousVersionId,
            activeVersionId = rotation.activeVersionId,
            overlapUntilEpochMillis = rotation.overlapUntilEpochMillis,
        )
    }

    fun getClientSecretStatus(organizationId: String, configId: String): OrganizationIdpSecretStatusOutcome
    {
        requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)
        val status = awsSecretsManagerService.getSecretLifecycleStatus(
            secretId = secretRef,
            region = configurationService.getOrgIdpSecretsRegion(),
        )

        val overlapActive = status.overlapUntilEpochMillis?.let { it > Instant.now().toEpochMilli() } ?: false
        return OrganizationIdpSecretStatusOutcome(
            secretRef = secretRef,
            disabled = status.disabled,
            currentVersionId = status.currentVersionId,
            previousVersionId = status.previousVersionId,
            pendingVersionId = status.pendingVersionId,
            overlapUntilEpochMillis = status.overlapUntilEpochMillis,
            rotationPhase = status.rotationPhase,
            overlapActive = overlapActive,
        )
    }

    fun rollbackClientSecretToPreviousVersion(
        organizationId: String,
        configId: String,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdpSecretRollbackOutcome
    {
        if (!configurationService.isSecretsRotationRollbackEnabled())
        {
            throw IllegalArgumentException("Secret rollback is disabled by configuration")
        }

        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_ROLLBACK",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        val status: SecretLifecycleStatus = awsSecretsManagerService.getSecretLifecycleStatus(
            secretId = secretRef,
            region = configurationService.getOrgIdpSecretsRegion(),
        )

        val overlapUntil = status.overlapUntilEpochMillis
            ?: throw IllegalArgumentException("Rollback window is not configured for this secret")
        if (Instant.now().toEpochMilli() > overlapUntil)
        {
            throw IllegalArgumentException("Rollback window has expired")
        }

        if (configurationService.isSecretsRotationRollbackRequireMonitorPhaseEnabled())
        {
            val phase = status.rotationPhase?.trim()?.uppercase()
            if (phase != "MONITOR" && phase != "ACTIVATE")
            {
                throw IllegalArgumentException("Rollback is only allowed when rotation phase is MONITOR or ACTIVATE")
            }
        }

        val rollbackVersion = status.previousVersionId
            ?: throw IllegalArgumentException("No previous secret version is available for rollback")

        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "ROLLBACK")

        awsSecretsManagerService.activateSecretVersion(
            secretId = secretRef,
            versionId = rollbackVersion,
            region = configurationService.getOrgIdpSecretsRegion(),
        )
        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "MONITOR")

        authAuditService.emit(
            action = "ORG_IDP_SECRET_ROLLBACK",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin rolled back IdP client secret",
            beforeSnapshot = "configId=${config.id};secretRef=$secretRef;activeVersion=${status.currentVersionId};overlapUntilEpochMillis=${status.overlapUntilEpochMillis}",
            afterSnapshot = "configId=${config.id};secretRef=$secretRef;activeVersion=${rollbackVersion};rotationPhase=MONITOR",
        )

        return OrganizationIdpSecretRollbackOutcome(
            secretRef = secretRef,
            rolledBackToVersionId = rollbackVersion,
            previousCurrentVersionId = status.currentVersionId,
        )
    }

    fun activateClientSecretVersion(
        organizationId: String,
        configId: String,
        versionId: String,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)

        if (versionId.isBlank())
        {
            throw IllegalArgumentException("Version ID is required")
        }

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_ACTIVATE",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        awsSecretsManagerService.activateSecretVersion(
            secretId = secretRef,
            versionId = versionId,
            region = configurationService.getOrgIdpSecretsRegion(),
        )
        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "ACTIVATE")

        authAuditService.emit(
            action = "ORG_IDP_SECRET_ACTIVATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin promoted staged IdP secret version",
            beforeSnapshot = "configId=${config.id};secretRef=$secretRef",
            afterSnapshot = "configId=${config.id};secretRef=$secretRef;activeVersion=${versionId};rotationPhase=ACTIVATE",
        )
    }

    fun disableClientSecret(
        organizationId: String,
        configId: String,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_DISABLE",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        awsSecretsManagerService.disableSecret(secretRef, configurationService.getOrgIdpSecretsRegion())

        authAuditService.emit(
            action = "ORG_IDP_SECRET_DISABLE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin disabled IdP client secret",
            beforeSnapshot = "configId=${config.id};secretRef=$secretRef;status=ACTIVE",
            afterSnapshot = "configId=${config.id};secretRef=$secretRef;status=DISABLED",
        )
    }

    fun enableClientSecret(
        organizationId: String,
        configId: String,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_ENABLE",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        awsSecretsManagerService.enableSecret(secretRef, configurationService.getOrgIdpSecretsRegion())

        authAuditService.emit(
            action = "ORG_IDP_SECRET_ENABLE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin enabled IdP client secret",
            beforeSnapshot = "configId=${config.id};secretRef=$secretRef;status=DISABLED",
            afterSnapshot = "configId=${config.id};secretRef=$secretRef;status=ACTIVE",
        )
    }

    fun retireClientSecret(
        organizationId: String,
        configId: String,
        recoveryWindowDays: Int,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val config = requireConfig(organizationId, configId)
        val secretRef = requireClientSecretRef(config)

        if (recoveryWindowDays !in 7..30)
        {
            throw IllegalArgumentException("Recovery window must be between 7 and 30 days")
        }

        adminActionGuardService.enforce(
            action = "ORG_IDP_SECRET_RETIRE",
            actorId = actor.id,
            context = adminApprovalContext,
        )

        awsSecretsManagerService.setRotationPhase(secretRef, configurationService.getOrgIdpSecretsRegion(), "RETIRE")
        awsSecretsManagerService.retireSecret(secretRef, configurationService.getOrgIdpSecretsRegion(), recoveryWindowDays)

        authAuditService.emit(
            action = "ORG_IDP_SECRET_RETIRE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = UUID.fromString(organizationId),
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin scheduled IdP client secret retirement",
            beforeSnapshot = "configId=${config.id};secretRef=$secretRef;status=ACTIVE",
            afterSnapshot = "configId=${config.id};secretRef=$secretRef;status=RETIRING;recoveryWindowDays=${recoveryWindowDays};rotationPhase=RETIRE",
        )
    }

    private fun requireClientSecretRef(config: com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig): String
    {
        return config.clientSecretRef?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Organization IdP config has no client secret reference")
    }

    private fun requireOrgAdminForOrganization(organizationId: String): com.docuhyphen.app.api.model.entity.AppUser
    {
        val currentUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")

        if (currentUser.role != AppUserRole.ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to manage IdP secrets")
        }

        val orgId = runCatching { UUID.fromString(organizationId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid organization ID format")

        val currentUserOrg = organizationRepository.findByAppUserIdAndPersonId(currentUser.id, currentUser.person?.id!!)
            ?: throw UnauthorizedException("User is not associated with an organization")

        if (currentUserOrg.id != orgId)
        {
            throw UnauthorizedException("User cannot manage another organization's IdP secrets")
        }

        return currentUser
    }

    private fun requireConfig(organizationId: String, configId: String): com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
    {
        val orgId = runCatching { UUID.fromString(organizationId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid organization ID format")
        val idpConfigId = runCatching { UUID.fromString(configId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid IdP config ID format")

        val config = organizationIdentityProviderConfigRepository.findById(idpConfigId)
            ?: throw IllegalArgumentException("Organization IdP config not found")

        if (config.organization?.id != orgId)
        {
            throw UnauthorizedException("Organization IdP config does not belong to target organization")
        }

        return config
    }
}






