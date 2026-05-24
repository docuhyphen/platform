package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigRequest
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class OrganizationIdentityProviderConfigService @Inject constructor(
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
)
{
    fun list(organizationId: String): List<OrganizationIdentityProviderConfig>
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val orgId = requireUuid(organizationId, "organization ID")

        return organizationIdentityProviderConfigRepository.findByOrganizationId(orgId)
            .also {
                authAuditService.emit(
                    action = "ORG_IDP_CONFIG_LIST",
                    outcome = "SUCCESS",
                    actorId = actor.id,
                    organizationId = orgId,
                    reason = "Organization admin listed IdP configs",
                )
            }
    }

    @Transactional
    fun create(
        organizationId: String,
        request: OrganizationIdpConfigRequest,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdentityProviderConfig
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val organization = requireOrganization(organizationId)

        adminActionGuardService.enforce(
            action = "ORG_IDP_CONFIG_CREATE",
            actorId = actor.id,
            context = adminApprovalContext,
            requireDualApproval = false,
        )

        validateRequest(request)
        ensureProviderUniqueWithinOrganization(organization.id, request.provider, null)

        val entity = OrganizationIdentityProviderConfig().apply {
            this.organization = organization
            applyRequest(this, request)
            this.createdDate = Timestamp.from(Instant.now())
            this.updatedDate = this.createdDate
            this.createdBy = actor.id
            this.updatedBy = actor.id
        }

        organizationIdentityProviderConfigRepository.save(entity)

        authAuditService.emit(
            action = "ORG_IDP_CONFIG_CREATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin created IdP config",
            beforeSnapshot = null,
            afterSnapshot = snapshot(entity),
        )

        return entity
    }

    @Transactional
    fun update(
        organizationId: String,
        configId: String,
        request: OrganizationIdpConfigRequest,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationIdentityProviderConfig
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val orgId = requireUuid(organizationId, "organization ID")

        adminActionGuardService.enforce(
            action = "ORG_IDP_CONFIG_UPDATE",
            actorId = actor.id,
            context = adminApprovalContext,
            requireDualApproval = !request.isActive,
        )

        validateRequest(request)

        val config = requireConfigBelongsToOrg(orgId, configId)
        val beforeSnapshot = snapshot(config)

        ensureProviderUniqueWithinOrganization(orgId, request.provider, config.id)

        applyRequest(config, request)
        config.updatedDate = Timestamp.from(Instant.now())
        config.updatedBy = actor.id

        organizationIdentityProviderConfigRepository.update(config)

        authAuditService.emit(
            action = "ORG_IDP_CONFIG_UPDATE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = orgId,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin updated IdP config",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = snapshot(config),
        )

        return config
    }

    @Transactional
    fun delete(
        organizationId: String,
        configId: String,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        val actor = requireOrgAdminForOrganization(organizationId)
        val orgId = requireUuid(organizationId, "organization ID")

        adminActionGuardService.enforce(
            action = "ORG_IDP_CONFIG_DELETE",
            actorId = actor.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )

        val config = requireConfigBelongsToOrg(orgId, configId)
        val beforeSnapshot = snapshot(config)

        organizationIdentityProviderConfigRepository.delete(config)

        authAuditService.emit(
            action = "ORG_IDP_CONFIG_DELETE",
            outcome = "SUCCESS",
            actorId = actor.id,
            organizationId = orgId,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin deleted IdP config",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = "deleted",
        )
    }

    private fun applyRequest(entity: OrganizationIdentityProviderConfig, request: OrganizationIdpConfigRequest)
    {
        entity.provider = request.provider.trim().uppercase()
        entity.clientId = request.clientId.trim()
        entity.clientSecretRef = request.clientSecretRef.trim()
        entity.tenantId = request.tenantId?.trim()?.takeIf { it.isNotBlank() }
        entity.scopes = normalizeList(request.scopes)
        entity.isActive = request.isActive
        entity.accessTokenExpiryMinutes = request.accessTokenExpiryMinutes
        entity.refreshTokenExpiryDays = request.refreshTokenExpiryDays
        entity.maxSessionDurationHours = request.maxSessionDurationHours
        entity.oidcIssuer = request.oidcIssuer?.trim()?.takeIf { it.isNotBlank() }
        entity.allowedAudiences = normalizeList(request.allowedAudiences)
        entity.allowedAlgs = normalizeList(request.allowedAlgs)
        entity.requiredClaims = normalizeList(request.requiredClaims)
    }

    private fun ensureProviderUniqueWithinOrganization(orgId: UUID, provider: String, excludedConfigId: UUID?)
    {
        val normalizedProvider = provider.trim().uppercase()
        val exists = organizationIdentityProviderConfigRepository.findByOrganizationId(orgId)
            .filter { it.id != excludedConfigId }
            .any { it.provider.trim().uppercase() == normalizedProvider }

        if (exists)
        {
            throw IllegalArgumentException("Provider configuration already exists for this organization")
        }
    }

    private fun validateRequest(request: OrganizationIdpConfigRequest)
    {
        if (request.provider.isBlank())
        {
            throw IllegalArgumentException("Provider is required")
        }

        if (request.clientId.isBlank())
        {
            throw IllegalArgumentException("Client ID is required")
        }

        if (request.clientSecretRef.isBlank())
        {
            throw IllegalArgumentException("Client secret reference is required")
        }

        if (request.accessTokenExpiryMinutes != null && request.accessTokenExpiryMinutes <= 0)
        {
            throw IllegalArgumentException("Access token expiry must be greater than 0")
        }

        if (request.refreshTokenExpiryDays != null && request.refreshTokenExpiryDays <= 0)
        {
            throw IllegalArgumentException("Refresh token expiry must be greater than 0")
        }

        if (request.maxSessionDurationHours != null && request.maxSessionDurationHours <= 0)
        {
            throw IllegalArgumentException("Max session duration must be greater than 0")
        }
    }

    private fun requireOrgAdminForOrganization(organizationId: String): com.docuhyphen.app.api.model.entity.AppUser
    {
        val currentUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")

        if (currentUser.role != AppUserRole.ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to manage organization IdP configuration")
        }

        val orgId = requireUuid(organizationId, "organization ID")
        val currentUserOrg = organizationRepository.findByAppUserIdAndPersonId(currentUser.id, currentUser.person?.id!!)
            ?: throw UnauthorizedException("User is not associated with an organization")

        if (currentUserOrg.id != orgId)
        {
            throw UnauthorizedException("User cannot manage another organization's IdP configuration")
        }

        return currentUser
    }

    private fun requireOrganization(organizationId: String): Organization
    {
        val orgId = requireUuid(organizationId, "organization ID")
        return organizationRepository.findById(orgId)
            ?: throw IllegalArgumentException("Organization not found")
    }

    private fun requireConfigBelongsToOrg(orgId: UUID, configId: String): OrganizationIdentityProviderConfig
    {
        val id = requireUuid(configId, "IdP config ID")
        val config = organizationIdentityProviderConfigRepository.findById(id)
            ?: throw IllegalArgumentException("IdP config not found")

        if (config.organization?.id != orgId)
        {
            throw UnauthorizedException("IdP config does not belong to target organization")
        }

        return config
    }

    private fun requireUuid(value: String, label: String): UUID
    {
        return runCatching { UUID.fromString(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label format") }
    }

    private fun normalizeList(values: List<String>): String?
    {
        val cleaned = values.map { it.trim() }.filter { it.isNotBlank() }
        return if (cleaned.isEmpty()) null else cleaned.joinToString(",")
    }

    private fun snapshot(config: OrganizationIdentityProviderConfig): String
    {
        return "id=${config.id};provider=${config.provider};clientId=${config.clientId};clientSecretRef=${config.clientSecretRef};tenantId=${config.tenantId};isActive=${config.isActive};scopes=${config.scopes};accessTokenExpiryMinutes=${config.accessTokenExpiryMinutes};refreshTokenExpiryDays=${config.refreshTokenExpiryDays};maxSessionDurationHours=${config.maxSessionDurationHours};oidcIssuer=${config.oidcIssuer};allowedAudiences=${config.allowedAudiences};allowedAlgs=${config.allowedAlgs};requiredClaims=${config.requiredClaims}"
    }
}

